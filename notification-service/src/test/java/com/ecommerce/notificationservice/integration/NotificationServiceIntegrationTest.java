package com.ecommerce.notificationservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void ownershipAndReadFlows() throws Exception {
        String adminToken = token(99L, "admin@example.com", "ADMIN");
        String userToken = token(1L, "user@example.com", "USER");
        String otherToken = token(2L, "other@example.com", "USER");

        String createBody = objectMapper.writeValueAsString(Map.of(
                "userId", 1,
                "type", "ORDER_CREATED",
                "title", "Order placed",
                "message", "Your order was created",
                "referenceId", "10",
                "referenceType", "ORDER"));

        mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.read").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long id = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(get("/api/notifications/" + id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/notifications/" + id + "/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void unauthenticatedRejected() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAllRead() throws Exception {
        String adminToken = token(99L, "admin@example.com", "ADMIN");
        String userToken = token(7L, "u7@example.com", "USER");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/notifications")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "userId", 7,
                                    "type", "PAYMENT_PENDING",
                                    "title", "Pay",
                                    "message", "pending",
                                    "referenceId", String.valueOf(i),
                                    "referenceType", "PAYMENT"))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    private String token(Long userId, String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
