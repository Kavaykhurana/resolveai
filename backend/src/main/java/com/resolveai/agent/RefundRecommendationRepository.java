package com.resolveai.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RefundRecommendationRepository extends JpaRepository<RefundRecommendation, Long> {
    Optional<RefundRecommendation> findByTicketId(Long ticketId);
    List<RefundRecommendation> findByStatusOrderByCreatedAtDesc(RefundRecommendation.Status status);
}