package com.resolveai.agent;

import com.resolveai.chat.Conversation;
import com.resolveai.chat.ConversationRepository;
import com.resolveai.chat.Message;
import com.resolveai.chat.MessageRepository;
import com.resolveai.order.Order;
import com.resolveai.order.OrderRepository;
import com.resolveai.user.User;
import com.resolveai.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final TicketRepository ticketRepository;
    private final RefundRecommendationRepository refundRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public AgentController(TicketRepository ticketRepository, RefundRecommendationRepository refundRepository,
                           ConversationRepository conversationRepository, MessageRepository messageRepository,
                           OrderRepository orderRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.refundRepository = refundRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    public record RefundDto(Long id, String orderNumber, BigDecimal amount, String reason, String status,
                            Instant decidedAt) {}

    public record TicketDto(Long id, Long conversationId, String customerName, String subject, String description,
                            String priority, String status, Instant createdAt, RefundDto refund) {}

    public record AgentConversationDto(Long id, Long customerId, String customerName, String status,
                                       Instant createdAt, Instant updatedAt) {}

    public record ReplyRequest(@NotBlank String content) {}

    public record DecisionRequest(@Pattern(regexp = "APPROVED|REJECTED") String decision) {}

    @GetMapping("/tickets")
    public List<TicketDto> tickets() {
        return ticketRepository.findAllByOrderByCreatedAtDesc().stream().map(t -> {
            RefundDto refund = refundRepository.findByTicketId(t.getId()).map(r -> {
                String orderNumber = orderRepository.findById(r.getOrderId())
                        .map(Order::getOrderNumber).orElse("unknown");
                return new RefundDto(r.getId(), orderNumber, r.getAmount(), r.getReason(),
                        r.getStatus().name(), r.getDecidedAt());
            }).orElse(null);
            String customerName = conversationRepository.findById(t.getConversationId())
                    .flatMap(c -> userRepository.findById(c.getCustomerId()))
                    .map(User::getName).orElse("Unknown customer");
            return new TicketDto(t.getId(), t.getConversationId(), customerName, t.getSubject(),
                    t.getDescription(), t.getPriority().name(), t.getStatus().name(), t.getCreatedAt(), refund);
        }).toList();
    }

    @GetMapping("/conversations")
    public List<AgentConversationDto> conversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc().stream().map(c -> {
            String name = userRepository.findById(c.getCustomerId()).map(User::getName).orElse("Unknown");
            return new AgentConversationDto(c.getId(), c.getCustomerId(), name, c.getStatus().name(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }).toList();
    }

    @PostMapping("/tickets/{id}/reply")
    @Transactional
    public Map<String, Object> reply(@PathVariable Long id, @RequestBody @Valid ReplyRequest request,
                                     @AuthenticationPrincipal User agent) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        messageRepository.save(new Message(ticket.getConversationId(), Message.Sender.HUMAN_AGENT, request.content()));
        if (ticket.getStatus() == Ticket.Status.OPEN) {
            ticket.setStatus(Ticket.Status.IN_REVIEW);
            ticketRepository.save(ticket);
        }
        conversationRepository.findById(ticket.getConversationId()).ifPresent(c -> {
            c.touch();
            conversationRepository.save(c);
        });
        return Map.of("ok", true, "ticketStatus", ticket.getStatus().name());
    }

    @PostMapping("/refunds/{id}/decide")
    @Transactional
    public Map<String, Object> decide(@PathVariable Long id, @RequestBody @Valid DecisionRequest request,
                                      @AuthenticationPrincipal User agent) {
        RefundRecommendation refund = refundRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund recommendation not found"));
        if (refund.getStatus() != RefundRecommendation.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This recommendation was already decided");
        }
        RefundRecommendation.Status decision = RefundRecommendation.Status.valueOf(request.decision());
        refund.setStatus(decision);
        refund.setDecidedBy(agent.getId());
        refund.setDecidedAt(Instant.now());
        refundRepository.save(refund);

        Ticket ticket = ticketRepository.findById(refund.getTicketId()).orElseThrow();
        String orderNumber = orderRepository.findById(refund.getOrderId()).map(Order::getOrderNumber).orElse("your order");
        String text = decision == RefundRecommendation.Status.APPROVED
                ? "Good news! Your refund of Rs." + refund.getAmount() + " for order " + orderNumber
                        + " has been approved by our support team. It will be credited to your original payment method within 5-7 business days."
                : "Your refund request of Rs." + refund.getAmount() + " for order " + orderNumber
                        + " was reviewed and could not be approved. Reply here if you'd like to discuss further.";
        messageRepository.save(new Message(ticket.getConversationId(), Message.Sender.HUMAN_AGENT, text));
        ticket.setStatus(decision == RefundRecommendation.Status.APPROVED ? Ticket.Status.RESOLVED : Ticket.Status.IN_REVIEW);
        ticketRepository.save(ticket);
        if (decision == RefundRecommendation.Status.APPROVED) {
            conversationRepository.findById(ticket.getConversationId()).ifPresent(c -> {
                c.setStatus(Conversation.Status.RESOLVED);
                c.touch();
                conversationRepository.save(c);
            });
        }
        return Map.of("ok", true, "status", decision.name());
    }

    @PostMapping("/tickets/{id}/resolve")
    @Transactional
    public Map<String, Object> resolve(@PathVariable Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ticket.setStatus(Ticket.Status.RESOLVED);
        ticketRepository.save(ticket);
        conversationRepository.findById(ticket.getConversationId()).ifPresent(c -> {
            c.setStatus(Conversation.Status.RESOLVED);
            c.touch();
            conversationRepository.save(c);
        });
        return Map.of("ok", true);
    }
}