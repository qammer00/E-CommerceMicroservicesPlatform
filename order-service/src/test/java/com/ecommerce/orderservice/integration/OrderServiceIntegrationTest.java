package com.ecommerce.orderservice.integration;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.orderservice.client.ProductCatalogService;
import com.ecommerce.orderservice.client.ProductDto;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.exception.ProductServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductCatalogService productCatalogService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void createAndLookupOrder_endToEndThroughApis() throws Exception {
        when(productCatalogService.getProduct("prod-1"))
                .thenReturn(new ProductDto("prod-1", "Keyboard", new BigDecimal("49.99"), 20, true));
        when(productCatalogService.reserveStock(eq("prod-1"), anyInt()))
                .thenReturn(new ProductDto("prod-1", "Keyboard", new BigDecimal("49.99"), 18, true));

        String token = userToken(1L, "buyer@example.com", "USER");

        String createBody = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", "prod-1", "quantity", 2)),
                "shippingAddress", "42 Integration Ave"));

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].productNameSnapshot").value("Keyboard"))
                .andExpect(jsonPath("$.data.totalAmount").value(99.98))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long orderId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId));

        mockMvc.perform(get("/api/orders/my-orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(orderId));
    }

    @Test
    void createOrder_returnsServiceUnavailableWhenProductDown() throws Exception {
        when(productCatalogService.getProduct("prod-down"))
                .thenThrow(new ProductServiceUnavailableException("Product Service is unavailable"));

        String token = userToken(1L, "buyer@example.com", "USER");
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", "prod-down", "quantity", 1)),
                "shippingAddress", "Nowhere"));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unauthorizedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/api/orders/my-orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanUpdateStatus_userCannot() throws Exception {
        when(productCatalogService.getProduct("prod-2"))
                .thenReturn(new ProductDto("prod-2", "Mouse", new BigDecimal("10.00"), 5, true));
        when(productCatalogService.reserveStock(eq("prod-2"), anyInt()))
                .thenReturn(new ProductDto("prod-2", "Mouse", new BigDecimal("10.00"), 4, true));

        String userToken = userToken(3L, "u3@example.com", "USER");
        String createBody = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", "prod-2", "quantity", 1)),
                "shippingAddress", "Admin Path"));

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long orderId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(patch("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CONFIRMED"))))
                .andExpect(status().isForbidden());

        String adminToken = userToken(99L, "admin@example.com", "ADMIN");
        mockMvc.perform(patch("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", OrderStatus.CONFIRMED.name()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void cancelOrder_viaApi() throws Exception {
        when(productCatalogService.getProduct("prod-3"))
                .thenReturn(new ProductDto("prod-3", "Cable", new BigDecimal("5.00"), 10, true));
        when(productCatalogService.reserveStock(eq("prod-3"), anyInt()))
                .thenReturn(new ProductDto("prod-3", "Cable", new BigDecimal("5.00"), 9, true));

        String token = userToken(4L, "c4@example.com", "USER");
        String createBody = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", "prod-3", "quantity", 1)),
                "shippingAddress", "Cancel Me"));

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long orderId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
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
