package com.ecommerce.paymentservice.dto;

import com.ecommerce.paymentservice.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @NotNull(message = "Order ID is required")
        @Positive(message = "Order ID must be a positive number")
        Long orderId,

        @NotNull(message = "Payment method is required")
        PaymentMethod method
) {
}
