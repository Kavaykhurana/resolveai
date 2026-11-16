package com.resolveai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.chat.ActionEvent;
import com.resolveai.chat.Conversation;
import com.resolveai.chat.ConversationRepository;
import com.resolveai.order.Order;
import com.resolveai.order.OrderRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class SupportTools {

    public static final String CTX_CUSTOMER_ID = "customerId";
    public static final String CTX_CONVERSATION_ID = "conversationId";
    public static final String CTX_ACTION_SINK = "actionSink";
    private static final int REFUND_WINDOW_DAYS = 30;

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final RefundRecommendationRepository refundRepository;
    private final ConversationRepository conversationRepository;
    private final AgentActionRepository actionRepository;
    private final ObjectMapper objectMapper;

    public SupportTools(OrderRepository orderRepository, TicketRepository ticketRepository,
                        RefundRecommendationRepository refundRepository,
                        ConversationRepository conversationRepository,
                        AgentActionRepository actionRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.refundRepository = refundRepository;
        this.conversationRepository = conversationRepository;
        this.actionRepository = actionRepository;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Check the current status of the customer's own order by its order number (e.g. SE-1002). "
            + "Returns status, items, tracking id and delivery dates. Never use for orders the customer does not own.")
    public String checkOrderStatus(
            @ToolParam(description = "The order number, e.g. SE-1002") String orderNumber,
            ToolContext ctx) {
        Long customerId = customerId(ctx);
        Map<String, Object> input = Map.of("orderNumber", String.valueOf(orderNumber));
        Order order = orderRepository.findByOrderNumberAndCustomerId(normalize(orderNumber), customerId).orElse(null);
        if (order == null) {
            String result = "No order " + orderNumber + " found for this customer. Ask them to double-check the order number.";
            record(ctx, "checkOrderStatus", input, result, false);
            return result;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderNumber", order.getOrderNumber());
        details.put("status", order.getStatus().name());
        details.put("items", order.getItems());
        details.put("totalAmount", order.getTotalAmount());
        details.put("orderedAt", order.getOrderedAt().toString());
        details.put("deliveredAt", order.getDeliveredAt() == null ? null : order.getDeliveredAt().toString());
        details.put("trackingId", order.getTrackingId());
        String result = toJson(details);
        record(ctx, "checkOrderStatus", input, result, true);
        return result;
    }

    @Tool(description = "Create a support ticket for an issue that needs follow-up by the support team. "
            + "Use when the customer has a complaint or request that cannot be resolved immediately in chat.")
    public String createTicket(
            @ToolParam(description = "Short ticket subject") String subject,
            @ToolParam(description = "Description of the issue") String description,
            @ToolParam(description = "Priority: LOW, MEDIUM or HIGH") String priority,
            ToolContext ctx) {
        Ticket.Priority p = parsePriority(priority);
        Ticket ticket = ticketRepository.save(new Ticket(conversationId(ctx), subject, description, p));
        touchConversation(ctx);
        String result = "Ticket #" + ticket.getId() + " created (priority " + p + "). The support team will follow up.";
        record(ctx, "createTicket", Map.of("subject", subject, "description", description, "priority", p.name()),
                result, true);
        return result;
    }

    @Tool(description = "Recommend a refund for the customer's own delivered order. This does NOT execute a refund: "
            + "it creates a pending recommendation that a human support agent must approve or reject. "
            + "Only valid for orders delivered within the last 30 days; amount must not exceed the order total.")
    @Transactional
    public String recommendRefund(
            @ToolParam(description = "The order number, e.g. SE-1003") String orderNumber,
            @ToolParam(description = "Refund amount in INR") double amount,
            @ToolParam(description = "Why the refund is being recommended") String reason,
            ToolContext ctx) {
        Long customerId = customerId(ctx);
        Map<String, Object> input = Map.of("orderNumber", String.valueOf(orderNumber),
                "amount", amount, "reason", String.valueOf(reason));
        Order order = orderRepository.findByOrderNumberAndCustomerId(normalize(orderNumber), customerId).orElse(null);
        if (order == null) {
            return failRefund(ctx, input, "No order " + orderNumber + " found for this customer.");
        }
        if (order.getStatus() != Order.Status.DELIVERED || order.getDeliveredAt() == null) {
            return failRefund(ctx, input, "Order " + order.getOrderNumber() + " has not been delivered yet, "
                    + "so it is not eligible for a refund. A refund can only be requested after delivery.");
        }
        long daysSinceDelivery = Duration.between(order.getDeliveredAt(), Instant.now()).toDays();
        if (daysSinceDelivery > REFUND_WINDOW_DAYS) {
            return failRefund(ctx, input, "Order " + order.getOrderNumber() + " was delivered " + daysSinceDelivery
                    + " days ago, outside the 30-day return window in the return-refund policy. "
                    + "A refund cannot be recommended; offer to escalate to a human agent if the customer insists.");
        }
        BigDecimal refundAmount = BigDecimal.valueOf(amount);
        if (amount <= 0 || refundAmount.compareTo(order.getTotalAmount()) > 0) {
            return failRefund(ctx, input, "Refund amount must be between 0 and the order total ("
                    + order.getTotalAmount() + ").");
        }
        Ticket ticket = ticketRepository.save(new Ticket(conversationId(ctx),
                "Refund request for " + order.getOrderNumber(), reason, Ticket.Priority.MEDIUM));
        RefundRecommendation refund = refundRepository.save(
                new RefundRecommendation(ticket.getId(), order.getId(), refundAmount, reason));
        touchConversation(ctx);
        String result = "Refund recommendation #" + refund.getId() + " for Rs." + refundAmount + " on order "
                + order.getOrderNumber() + " is PENDING human approval (ticket #" + ticket.getId() + "). "
                + "Tell the customer a support agent will review it shortly.";
        record(ctx, "recommendRefund", input, result, true);
        return result;
    }

    @Tool(description = "Escalate the conversation to a human support agent. Use when the customer explicitly asks "
            + "for a human, when the issue is complex or outside policy, or when you cannot resolve it confidently.")
    @Transactional
    public String escalateToHuman(
            @ToolParam(description = "Summary of why the conversation is being escalated") String reason,
            ToolContext ctx) {
        Conversation conversation = conversationRepository.findById(conversationId(ctx)).orElseThrow();
        conversation.setStatus(Conversation.Status.ESCALATED);
        conversation.touch();
        conversationRepository.save(conversation);
        Ticket ticket = ticketRepository.save(new Ticket(conversation.getId(),
                "Escalation: human assistance requested", reason, Ticket.Priority.HIGH));
        String result = "Conversation escalated to a human agent (ticket #" + ticket.getId()
                + "). A support agent will take over and reply in this chat.";
        record(ctx, "escalateToHuman", Map.of("reason", String.valueOf(reason)), result, true);
        return result;
    }

    private String failRefund(ToolContext ctx, Map<String, Object> input, String message) {
        record(ctx, "recommendRefund", input, message, false);
        return message;
    }

    private void record(ToolContext ctx, String toolName, Map<String, Object> input, String result, boolean success) {
        AgentAction action = actionRepository.save(new AgentAction(
                conversationId(ctx), toolName, toJson(input), result, success));
        @SuppressWarnings("unchecked")
        Consumer<ActionEvent> sink = (Consumer<ActionEvent>) ctx.getContext().get(CTX_ACTION_SINK);
        if (sink != null) {
            sink.accept(new ActionEvent(action.getId(), toolName, result, success));
        }
    }

    private void touchConversation(ToolContext ctx) {
        conversationRepository.findById(conversationId(ctx)).ifPresent(c -> {
            c.touch();
            conversationRepository.save(c);
        });
    }

    private Long customerId(ToolContext ctx) {
        return (Long) ctx.getContext().get(CTX_CUSTOMER_ID);
    }

    private Long conversationId(ToolContext ctx) {
        return (Long) ctx.getContext().get(CTX_CONVERSATION_ID);
    }

    private String normalize(String orderNumber) {
        return orderNumber == null ? "" : orderNumber.trim().toUpperCase();
    }

    private Ticket.Priority parsePriority(String priority) {
        try {
            return Ticket.Priority.valueOf(priority == null ? "MEDIUM" : priority.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Ticket.Priority.MEDIUM;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}