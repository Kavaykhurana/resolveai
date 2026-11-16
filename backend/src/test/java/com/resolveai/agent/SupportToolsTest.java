package com.resolveai.agent;

import com.resolveai.chat.ActionEvent;
import com.resolveai.chat.Conversation;
import com.resolveai.chat.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.kb.ingest-enabled=false")
@Transactional
class SupportToolsTest {

    @Autowired SupportTools tools;
    @Autowired ConversationRepository conversationRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired RefundRecommendationRepository refundRepository;
    @Autowired AgentActionRepository actionRepository;

    private Long conversationId;
    private ToolContext ctx;
    private final List<ActionEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Conversation c = new Conversation();
        c.setCustomerId(1L);
        conversationId = conversationRepository.save(c).getId();
        Consumer<ActionEvent> sink = events::add;
        ctx = new ToolContext(Map.of(
                SupportTools.CTX_CUSTOMER_ID, 1L,
                SupportTools.CTX_CONVERSATION_ID, conversationId,
                SupportTools.CTX_ACTION_SINK, sink));
    }

    @Test
    void checkOrderStatusReturnsOwnOrderDetails() {
        String result = tools.checkOrderStatus("se-1002", ctx);
        assertThat(result).contains("SE-1002").contains("SHIPPED").contains("TRK-88421-IN");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).success()).isTrue();
        assertThat(actionRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)).hasSize(1);
    }

    @Test
    void checkOrderStatusRefusesForeignOrder() {
        ToolContext foreignCtx = new ToolContext(Map.of(
                SupportTools.CTX_CUSTOMER_ID, 999L,
                SupportTools.CTX_CONVERSATION_ID, conversationId,
                SupportTools.CTX_ACTION_SINK, (Consumer<ActionEvent>) events::add));
        String result = tools.checkOrderStatus("SE-1002", foreignCtx);
        assertThat(result).contains("No order").contains("found for this customer");
        assertThat(events.get(0).success()).isFalse();
    }

    @Test
    void createTicketPersistsTicketAndAction() {
        String result = tools.createTicket("Wrong item received", "Ordered shoes, got a lamp", "high", ctx);
        assertThat(result).contains("Ticket #").contains("HIGH");
        List<Ticket> tickets = ticketRepository.findAllByOrderByCreatedAtDesc();
        assertThat(tickets).anyMatch(t -> t.getConversationId().equals(conversationId)
                && t.getPriority() == Ticket.Priority.HIGH);
    }

    @Test
    void recommendRefundCreatesPendingRecommendation() {
        String result = tools.recommendRefund("SE-1003", 2499.0, "Item not as described", ctx);
        assertThat(result).contains("PENDING human approval");
        Ticket ticket = ticketRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversationId).orElseThrow();
        RefundRecommendation refund = refundRepository.findByTicketId(ticket.getId()).orElseThrow();
        assertThat(refund.getStatus()).isEqualTo(RefundRecommendation.Status.PENDING);
        assertThat(refund.getAmount()).isEqualByComparingTo("2499.0");
    }

    @Test
    void refundOutsideWindowIsRefused() {
        String result = tools.recommendRefund("SE-1004", 4999.0, "Changed my mind", ctx);
        assertThat(result).contains("outside the 30-day return window");
        assertThat(events.get(0).success()).isFalse();
    }

    @Test
    void refundForUndeliveredOrderIsRefused() {
        String result = tools.recommendRefund("SE-1002", 1798.0, "Never arrived", ctx);
        assertThat(result).contains("not been delivered");
    }

    @Test
    void refundAmountAboveTotalIsRefused() {
        String result = tools.recommendRefund("SE-1003", 9999.0, "Overcharge", ctx);
        assertThat(result).contains("must be between 0 and the order total");
    }

    @Test
    void escalateMarksConversationAndCreatesHighTicket() {
        String result = tools.escalateToHuman("Customer received a damaged high-value item", ctx);
        assertThat(result).contains("escalated");
        Conversation c = conversationRepository.findById(conversationId).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(Conversation.Status.ESCALATED);
        Ticket ticket = ticketRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversationId).orElseThrow();
        assertThat(ticket.getPriority()).isEqualTo(Ticket.Priority.HIGH);
    }
}