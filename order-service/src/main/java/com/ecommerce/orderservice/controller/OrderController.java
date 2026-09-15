package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.ApiResponse;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.dto.UpdateOrderStatusRequest;
import com.ecommerce.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order lifecycle APIs")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create an order")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest) {
        OrderResponse order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Order created successfully",
                order,
                requestId(httpRequest)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by id")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Order retrieved successfully",
                orderService.getOrderById(id),
                requestId(httpRequest)));
    }

    @GetMapping
    @Operation(summary = "List all orders (ADMIN)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> listOrders(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Orders retrieved successfully",
                orderService.listOrders(),
                requestId(httpRequest)));
    }

    @GetMapping("/my-orders")
    @Operation(summary = "List current user's orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> listMyOrders(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "My orders retrieved successfully",
                orderService.listMyOrders(),
                requestId(httpRequest)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status (ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Order status updated successfully",
                orderService.updateStatus(id, request),
                requestId(httpRequest)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Order cancelled successfully",
                orderService.cancelOrder(id),
                requestId(httpRequest)));
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString();
    }
}
