package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(
        @NotNull(message = "Active status is required")
        Boolean active
) {
}
