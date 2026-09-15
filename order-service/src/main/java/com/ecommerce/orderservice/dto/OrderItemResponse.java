package com.ecommerce.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        String productId,
        String productNameSnapshot,
        BigDecimal unitPriceSnapshot,
        Integer quantity,
        BigDecimal subtotal
) {
}
