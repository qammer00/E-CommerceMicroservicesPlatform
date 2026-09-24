package com.ecommerce.paymentservice.integration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.paymentservice.client.OrderDto;
import com.ecommerce.paymentservice.client.OrderLookupService;
import com.ecommerce.paymentservice.entity.PaymentStatus;
import com.ecommerce.paymentservice.exception.PaymentOrderUnavailableException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private OrderLookupService orderLookupService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void createGetAndAdminFlows_endToEnd() throws Exception {
        when(orderLookupService.getOrder(20L))
                .thenReturn(new OrderDto(20L, 1L, new BigDecimal("99.50"), "PENDING"));

        String userToken = userToken(1L, "buyer@example.com", "USER");
        String createBody = objectMapper.writeValueAsString(Map.of(
                "orderId", 20,
                "method", "CASH_ON_DELIVERY"));

        String createResponse = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(99.50))
                .andExpect(jsonPath("$.data.currency").value("PKR"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.paymentReference").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long paymentId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/payments/" + paymentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paymentId));

        mockMvc.perform(get("/api/payments/my-payments")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(paymentId));

        mockMvc.perform(get("/api/payments")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/payments/" + paymentId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PROCESSING"))))
                .andExpect(status().isForbidden());

        String adminToken = userToken(99L, "admin@example.com", "ADMIN");
        mockMvc.perform(get("/api/payments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + paymentId + ")].id").exists());

        mockMvc.perform(patch("/api/payments/" + paymentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", PaymentStatus.PROCESSING.name()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void unauthenticatedCreate_isRejected() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", 1,
                                "method", "CARD"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPayment_returnsServiceUnavailableWhenOrderDown() throws Exception {
        when(orderLookupService.getOrder(30L))
                .thenThrow(new PaymentOrderUnavailableException("Order Service is unavailable"));

        String token = userToken(1L, "buyer@example.com", "USER");
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", 30,
                                "method", "CARD"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void userCannotViewAnotherUsersPayment() throws Exception {
        when(orderLookupService.getOrder(40L))
                .thenReturn(new OrderDto(40L, 1L, new BigDecimal("10.00"), "PENDING"));

        String ownerToken = userToken(1L, "owner@example.com", "USER");
        String createResponse = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", 40,
                                "method", "CARD"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long paymentId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        String otherToken = userToken(2L, "other@example.com", "USER");
        mockMvc.perform(get("/api/payments/" + paymentId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void validation_rejectsMissingFieldsAndInvalidMethod() throws Exception {
        String token = userToken(1L, "buyer@example.com", "USER");

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", -1,
                                "method", "CARD"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"method\":\"BITCOIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateActivePayment_isRejected_andRetryAfterFailedAllowed() throws Exception {
        when(orderLookupService.getOrder(50L))
                .thenReturn(new OrderDto(50L, 3L, new BigDecimal("40.00"), "CONFIRMED"));

        String token = userToken(3L, "u3@example.com", "USER");
        String body = objectMapper.writeValueAsString(Map.of("orderId", 50, "method", "CARD"));

        String first = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long paymentId = objectMapper.readTree(first).path("data").path("id").asLong();

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());

        String adminToken = userToken(99L, "admin@example.com", "ADMIN");
        mockMvc.perform(patch("/api/payments/" + paymentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "FAILED"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void paymentReference_isUniqueInDatabase() {
        var first = paymentRepository.save(newPayment(3L, "PAY-UNIQUE-1"));
        assert first.getId() != null;
        var second = newPayment(4L, "PAY-UNIQUE-1");
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            paymentRepository.saveAndFlush(second);
        });
    }

    private com.ecommerce.paymentservice.entity.Payment newPayment(Long orderId, String reference) {
        var payment = new com.ecommerce.paymentservice.entity.Payment();
        payment.setPaymentReference(reference);
        payment.setOrderId(orderId);
        payment.setUserId(1L);
        payment.setAmount(new BigDecimal("1.00"));
        payment.setCurrency("PKR");
        payment.setMethod(com.ecommerce.paymentservice.entity.PaymentMethod.CARD);
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private String userToken(Long userId, String email, String role) {
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
