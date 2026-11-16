package com.resolveai.chat;

import com.resolveai.user.Role;
import com.resolveai.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ConversationRepository conversationRepository;
    private final ChatService chatService;

    public ChatController(ConversationRepository conversationRepository, ChatService chatService) {
        this.conversationRepository = conversationRepository;
        this.chatService = chatService;
    }

    public record ChatRequest(@NotBlank @Size(max = 4000) String message) {}

    @PostMapping(value = "/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable Long conversationId,
                           @RequestBody @Valid ChatRequest request,
                           @AuthenticationPrincipal User user) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.getCustomerId().equals(user.getId()) && user.getRole() != Role.AGENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your conversation");
        }
        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.streamChat(conversationId, request.message().trim(), user, emitter);
        return emitter;
    }
}