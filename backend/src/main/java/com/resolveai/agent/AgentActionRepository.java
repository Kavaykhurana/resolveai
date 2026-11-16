package com.resolveai.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AgentActionRepository extends JpaRepository<AgentAction, Long> {
    List<AgentAction> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}