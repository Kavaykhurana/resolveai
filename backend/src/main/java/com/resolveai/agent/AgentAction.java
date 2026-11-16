package com.resolveai.agent;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_actions")
public class AgentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "tool_name", nullable = false)
    private String toolName;

    @Column(name = "input_json", nullable = false, columnDefinition = "text")
    private String inputJson;

    @Column(name = "result_json", nullable = false, columnDefinition = "text")
    private String resultJson;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AgentAction() {}

    public AgentAction(Long conversationId, String toolName, String inputJson, String resultJson, boolean success) {
        this.conversationId = conversationId;
        this.toolName = toolName;
        this.inputJson = inputJson;
        this.resultJson = resultJson;
        this.success = success;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getToolName() { return toolName; }
    public String getInputJson() { return inputJson; }
    public String getResultJson() { return resultJson; }
    public boolean isSuccess() { return success; }
    public Instant getCreatedAt() { return createdAt; }
}