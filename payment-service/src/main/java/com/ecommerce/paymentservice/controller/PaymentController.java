package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.dto.ApiResponse;
import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.dto.UpdatePaymentStatusRequest;
import com.ecommerce.paymentservice.service.PaymentService;
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
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Payment lifecycle APIs")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Create a payment for an order")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {
        PaymentResponse payment = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Payment created successfully",
                payment,
                requestId(httpRequest)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by id")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Payment retrieved successfully",
                paymentService.getPaymentById(id),
                requestId(httpRequest)));
    }

    @GetMapping("/my-payments")
    @Operation(summary = "List current user's payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listMyPayments(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "My payments retrieved successfully",
                paymentService.listMyPayments(),
                requestId(httpRequest)));
    }

    @GetMapping
    @Operation(summary = "List all payments (ADMIN)")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listPayments(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Payments retrieved successfully",
                paymentService.listPayments(),
                requestId(httpRequest)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update payment status (ADMIN)")
    public ResponseEntity<ApiResponse<PaymentResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePaymentStatusRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Payment status updated successfully",
                paymentService.updateStatus(id, request),
                requestId(httpRequest)));
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString();
    }
}
