package com.resolveai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.kb.ingest-enabled=false")
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", "Test User", "email", email, "password", "password123"));
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void registerThenLoginThenAccessProtectedEndpoint() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        String token = register(email);

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", "password123"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Test User"));

        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        register(email);
        String body = objectMapper.writeValueAsString(
                Map.of("name", "Dup", "email", email, "password", "password123"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "customer@demo.com", "password", "wrong-password"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void seededDemoAccountsCanLogin() throws Exception {
        for (String email : new String[]{"customer@demo.com", "agent@demo.com"}) {
            String body = objectMapper.writeValueAsString(Map.of("email", email, "password", "demo1234"));
            String response = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(response).get("token").asText()).isNotBlank();
        }
    }
}