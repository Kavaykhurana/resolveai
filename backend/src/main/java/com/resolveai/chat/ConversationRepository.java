package com.resolveai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByCustomerIdOrderByUpdatedAtDesc(Long customerId);
    List<Conversation> findAllByOrderByUpdatedAtDesc();
}