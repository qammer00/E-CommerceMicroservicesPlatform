package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Size(max = 100, message = "SKU must be at most 100 characters")
        String sku,

        @DecimalMin(value = "0.01", inclusive = true, message = "Price must be positive")
        BigDecimal price,

        @Min(value = 0, message = "Stock quantity must not be negative")
        Integer stockQuantity,

        @Size(max = 100, message = "Category must be at most 100 characters")
        String category,

        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl,

        Boolean active
) {
}
