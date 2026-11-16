package com.resolveai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.chat.Conversation;
import com.resolveai.chat.ConversationRepository;
import com.resolveai.chat.Message;
import com.resolveai.chat.MessageRepository;
import com.resolveai.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.kb.ingest-enabled=false")
@AutoConfigureMockMvc
class AgentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired RefundRecommendationRepository refundRepository;
    @Autowired OrderRepository orderRepository;

    private String agentToken;
    private String customerToken;
    private Long conversationId;
    private Long ticketId;
    private Long refundId;

    private String login(String email) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", "demo1234"));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        agentToken = login("agent@demo.com");
        customerToken = login("customer@demo.com");

        Conversation c = new Conversation();
        c.setCustomerId(1L);
        conversationId = conversationRepository.save(c).getId();
        Ticket ticket = ticketRepository.save(new Ticket(conversationId,
                "Refund request for SE-1003", "Item not as described", Ticket.Priority.MEDIUM));
        ticketId = ticket.getId();
        Long orderId = orderRepository.findByOrderNumber("SE-1003").orElseThrow().getId();
        refundId = refundRepository.save(new RefundRecommendation(
                ticketId, orderId, new BigDecimal("2499.00"), "Item not as described")).getId();
    }

    @Test
    void agentSeesTicketQueueWithRefund() throws Exception {
        mockMvc.perform(get("/api/agent/tickets").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + ticketId + ")].refund.status").value("PENDING"))
                .andExpect(jsonPath("$[?(@.id == " + ticketId + ")].customerName").value("Aarav Sharma"));
    }

    @Test
    void customerCannotAccessAgentApi() throws Exception {
        mockMvc.perform(get("/api/agent/tickets").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveRefundResolvesTicketAndNotifiesCustomer() throws Exception {
        mockMvc.perform(post("/api/agent/refunds/" + refundId + "/decide")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(refundRepository.findById(refundId).orElseThrow().getStatus())
                .isEqualTo(RefundRecommendation.Status.APPROVED);
        assertThat(ticketRepository.findById(ticketId).orElseThrow().getStatus())
                .isEqualTo(Ticket.Status.RESOLVED);
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(Conversation.Status.RESOLVED);
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .anyMatch(m -> m.getSender() == Message.Sender.HUMAN_AGENT && m.getContent().contains("approved"));
    }

    @Test
    void doubleDecisionIsConflict() throws Exception {
        mockMvc.perform(post("/api/agent/refunds/" + refundId + "/decide")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/refunds/" + refundId + "/decide")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void humanReplyAppearsInTranscriptAndMovesTicketToReview() throws Exception {
        mockMvc.perform(post("/api/agent/tickets/" + ticketId + "/reply")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hi, I am Priya from support. Looking into this now.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketStatus").value("IN_REVIEW"));

        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .anyMatch(m -> m.getSender() == Message.Sender.HUMAN_AGENT && m.getContent().contains("Priya"));
    }

    @Test
    void resolveTicketClosesConversation() throws Exception {
        mockMvc.perform(post("/api/agent/tickets/" + ticketId + "/resolve")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(Conversation.Status.RESOLVED);
    }
}