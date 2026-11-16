package com.resolveai.chat;

import com.resolveai.user.User;
import com.resolveai.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "app.kb.ingest-enabled=false")
class ChatServiceTest {

    @MockitoBean ChatModel chatModel;
    @MockitoBean VectorStore vectorStore;

    @Autowired ChatService chatService;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired UserRepository userRepository;

    @Test
    void streamChatInjectsRagContextAndPersistsBothMessages() throws Exception {
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("Refunds are allowed within 30 days of delivery.",
                        Map.of("source", "return-refund-policy.md"))));
        ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage("You can return items within 30 days of delivery."))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        Conversation c = new Conversation();
        c.setCustomerId(1L);
        Long conversationId = conversationRepository.save(c).getId();
        User customer = userRepository.findById(1L).orElseThrow();

        SseEmitter emitter = new SseEmitter(30_000L);
        chatService.streamChat(conversationId, "What is your refund policy?", customer, emitter);

        List<Message> messages = awaitMessages(conversationId, 2);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getSender()).isEqualTo(Message.Sender.USER);
        assertThat(messages.get(0).getContent()).isEqualTo("What is your refund policy?");
        assertThat(messages.get(1).getSender()).isEqualTo(Message.Sender.AI);
        assertThat(messages.get(1).getContent()).contains("30 days");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        String systemText = promptCaptor.getValue().getInstructions().stream()
                .filter(m -> m instanceof org.springframework.ai.chat.messages.SystemMessage)
                .map(org.springframework.ai.chat.messages.Message::getText)
                .reduce("", String::concat);
        assertThat(systemText).contains("return-refund-policy.md").contains("30 days of delivery");
    }

    @Test
    void streamChatSendsErrorEventWhenModelFails() throws Exception {
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("Gemini unavailable")));

        Conversation c = new Conversation();
        c.setCustomerId(1L);
        Long conversationId = conversationRepository.save(c).getId();
        User customer = userRepository.findById(1L).orElseThrow();

        SseEmitter emitter = new SseEmitter(30_000L);
        chatService.streamChat(conversationId, "Hello?", customer, emitter);

        verify(chatModel, org.mockito.Mockito.timeout(20_000)).stream(any(Prompt.class));
        Thread.sleep(500);
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSender()).isEqualTo(Message.Sender.USER);
    }

    private List<Message> awaitMessages(Long conversationId, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        while (messages.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        }
        return messages;
    }
}