package com.resolveai.chat;

import com.resolveai.agent.AgentAction;
import com.resolveai.agent.AgentActionRepository;
import com.resolveai.user.Role;
import com.resolveai.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AgentActionRepository agentActionRepository;

    public ConversationController(ConversationRepository conversationRepository,
                                  MessageRepository messageRepository,
                                  AgentActionRepository agentActionRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.agentActionRepository = agentActionRepository;
    }

    public record ConversationDto(Long id, String status, Instant createdAt, Instant updatedAt) {
        static ConversationDto from(Conversation c) {
            return new ConversationDto(c.getId(), c.getStatus().name(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record MessageDto(Long id, String sender, String content, Instant createdAt) {
        static MessageDto from(Message m) {
            return new MessageDto(m.getId(), m.getSender().name(), m.getContent(), m.getCreatedAt());
        }
    }

    public record ActionDto(Long id, String toolName, String inputJson, String resultJson,
                            boolean success, Instant createdAt) {
        static ActionDto from(AgentAction a) {
            return new ActionDto(a.getId(), a.getToolName(), a.getInputJson(), a.getResultJson(),
                    a.isSuccess(), a.getCreatedAt());
        }
    }

    @PostMapping
    public ConversationDto create(@AuthenticationPrincipal User user) {
        Conversation c = new Conversation();
        c.setCustomerId(user.getId());
        return ConversationDto.from(conversationRepository.save(c));
    }

    @GetMapping
    public List<ConversationDto> mine(@AuthenticationPrincipal User user) {
        return conversationRepository.findByCustomerIdOrderByUpdatedAtDesc(user.getId())
                .stream().map(ConversationDto::from).toList();
    }

    @GetMapping("/{id}/messages")
    public Map<String, Object> transcript(@PathVariable Long id, @AuthenticationPrincipal User user) {
        Conversation c = conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!c.getCustomerId().equals(user.getId()) && user.getRole() != Role.AGENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your conversation");
        }
        List<MessageDto> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(id)
                .stream().map(MessageDto::from).toList();
        List<ActionDto> actions = agentActionRepository.findByConversationIdOrderByCreatedAtAsc(id)
                .stream().map(ActionDto::from).toList();
        return Map.of("conversation", ConversationDto.from(c), "messages", messages, "actions", actions);
    }
}