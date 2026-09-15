package com.ecommerce.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "At least one order item is required")
        @Size(max = 50, message = "Too many order items")
        @Valid
        List<OrderItemRequest> items,

        @NotBlank(message = "Shipping address is required")
        @Size(max = 500, message = "Shipping address must be at most 500 characters")
        String shippingAddress
) {
}
