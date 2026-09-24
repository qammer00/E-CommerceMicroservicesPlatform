package com.ecommerce.paymentservice.dto;

import com.ecommerce.paymentservice.entity.PaymentMethod;
import com.ecommerce.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        String paymentReference,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
