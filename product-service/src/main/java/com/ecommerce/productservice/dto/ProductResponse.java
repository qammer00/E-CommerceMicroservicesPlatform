package com.ecommerce.productservice.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id,
        String name,
        String description,
        String sku,
        BigDecimal price,
        Integer stockQuantity,
        String category,
        String imageUrl,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
