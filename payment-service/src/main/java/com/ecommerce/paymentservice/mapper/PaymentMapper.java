package com.ecommerce.paymentservice.mapper;

import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.entity.Payment;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentReference(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
