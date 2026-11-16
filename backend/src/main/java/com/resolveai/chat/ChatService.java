package com.resolveai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.agent.SupportTools;
import com.resolveai.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT = """
            You are ResolveAI, the autonomous customer support agent for ShopEase, an Indian e-commerce store.
            Currency is Indian Rupees (Rs.). Be friendly, concise and professional.

            Rules:
            1. For policy questions (returns, refunds, shipping, warranty, payments, cancellation), answer ONLY from
               the KNOWLEDGE BASE CONTEXT below and mention the policy name you used. If the context does not cover
               the question, say you are not sure and offer to escalate to a human agent. Never invent policies.
            2. For order questions, ALWAYS use the checkOrderStatus tool with the order number. Never guess or
               fabricate order data. If the customer has not given an order number, ask for it.
            3. Use createTicket for complaints or requests needing follow-up.
            4. Use recommendRefund when the customer asks for a refund and the order seems eligible. The tool creates
               a PENDING recommendation for a human agent to approve - tell the customer it is pending review,
               never promise that a refund is already approved or processed.
            5. Use escalateToHuman when the customer asks for a human, is distressed, reports damage to a high-value
               item, the issue is outside policy, or you cannot resolve it confidently.
            6. You may call multiple tools in one turn when needed. After tools run, summarise the outcome clearly.
            7. Keep answers short (2-5 sentences) unless detail is genuinely needed.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final SupportTools supportTools;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public ChatService(ChatModel chatModel, VectorStore vectorStore, SupportTools supportTools,
                       ConversationRepository conversationRepository, MessageRepository messageRepository,
                       ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.vectorStore = vectorStore;
        this.supportTools = supportTools;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    @Async("chatExecutor")
    public void streamChat(Long conversationId, String message, User user, SseEmitter emitter) {
        Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();
        messageRepository.save(new Message(conversationId, Message.Sender.USER, message));
        conversation.touch();
        conversationRepository.save(conversation);

        List<org.springframework.ai.chat.messages.Message> history = loadHistory(conversationId);
        String context = retrieveContext(message);

        Consumer<ActionEvent> sink = event -> send(emitter, "action", toJson(event));
        StringBuilder full = new StringBuilder();

        try {
            chatClient.prompt()
                    .system(SYSTEM_PROMPT + "\nKNOWLEDGE BASE CONTEXT:\n" + context)
                    .messages(history)
                    .user(message)
                    .tools(supportTools)
                    .toolContext(Map.of(
                            SupportTools.CTX_CUSTOMER_ID, user.getId(),
                            SupportTools.CTX_CONVERSATION_ID, conversationId,
                            SupportTools.CTX_ACTION_SINK, sink))
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        full.append(chunk);
                        send(emitter, "token", chunk);
                    })
                    .doOnError(e -> {
                        log.error("Chat stream failed for conversation {}: {}", conversationId, e.getMessage());
                        send(emitter, "error", "Sorry, I hit a temporary problem reaching the AI service. "
                                + "Your message is saved - please try again in a moment.");
                        emitter.complete();
                    })
                    .doOnComplete(() -> {
                        String answer = full.toString();
                        if (!answer.isBlank()) {
                            messageRepository.save(new Message(conversationId, Message.Sender.AI, answer));
                        }
                        send(emitter, "done", answer);
                        emitter.complete();
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("Chat failed for conversation {}: {}", conversationId, e.getMessage());
            send(emitter, "error", "Sorry, I hit a temporary problem reaching the AI service. "
                    + "Your message is saved - please try again in a moment.");
            emitter.complete();
        }
    }

    private List<org.springframework.ai.chat.messages.Message> loadHistory(Long conversationId) {
        List<Message> recent = new ArrayList<>(
                messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId));
        Collections.reverse(recent);
        // The latest USER message is passed separately as .user(...); drop it from history
        if (!recent.isEmpty() && recent.get(recent.size() - 1).getSender() == Message.Sender.USER) {
            recent.remove(recent.size() - 1);
        }
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        for (Message m : recent) {
            switch (m.getSender()) {
                case USER -> history.add(new UserMessage(m.getContent()));
                case AI -> history.add(new AssistantMessage(m.getContent()));
                case HUMAN_AGENT -> history.add(new AssistantMessage("[Human support agent]: " + m.getContent()));
            }
        }
        return history;
    }

    private String retrieveContext(String query) {
        try {
            List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query).topK(4).similarityThreshold(0.55).build());
            if (docs == null || docs.isEmpty()) {
                return "(no relevant policy excerpts found)";
            }
            StringBuilder sb = new StringBuilder();
            for (Document doc : docs) {
                sb.append("[source: ").append(doc.getMetadata().getOrDefault("source", "unknown")).append("]\n")
                        .append(doc.getText()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("RAG retrieval failed, continuing without context: {}", e.getMessage());
            return "(knowledge base temporarily unavailable)";
        }
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE send failed (client likely disconnected): {}", e.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}