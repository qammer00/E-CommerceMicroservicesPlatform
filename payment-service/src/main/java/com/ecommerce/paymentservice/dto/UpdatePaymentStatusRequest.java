package com.ecommerce.paymentservice.dto;

import com.ecommerce.paymentservice.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdatePaymentStatusRequest(
        @NotNull(message = "Status is required")
        PaymentStatus status
) {
}
