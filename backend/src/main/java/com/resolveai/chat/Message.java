package com.resolveai.chat;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {

    public enum Sender { USER, AI, HUMAN_AGENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sender sender;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Message() {}

    public Message(Long conversationId, Sender sender, String content) {
        this.conversationId = conversationId;
        this.sender = sender;
        this.content = content;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Sender getSender() { return sender; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}