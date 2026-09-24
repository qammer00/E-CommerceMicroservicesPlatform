package com.ecommerce.paymentservice.client;

import java.math.BigDecimal;

/**
 * Slim view of Order Service {@code OrderResponse} — only fields needed for payment validation.
 */
public record OrderDto(
        Long id,
        Long userId,
        BigDecimal totalAmount,
        String status
) {
}
