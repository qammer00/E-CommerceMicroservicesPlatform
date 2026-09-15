package com.ecommerce.orderservice.client;

import java.math.BigDecimal;

public record ProductDto(
        String id,
        String name,
        BigDecimal price,
        Integer stockQuantity,
        boolean active
) {
}
