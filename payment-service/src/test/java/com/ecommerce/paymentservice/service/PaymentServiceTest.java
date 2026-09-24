package com.ecommerce.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.paymentservice.client.OrderDto;
import com.ecommerce.paymentservice.client.OrderLookupService;
import com.ecommerce.paymentservice.config.PaymentProperties;
import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.dto.UpdatePaymentStatusRequest;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.entity.PaymentMethod;
import com.ecommerce.paymentservice.entity.PaymentStatus;
import com.ecommerce.paymentservice.exception.ForbiddenAccessException;
import com.ecommerce.paymentservice.exception.InvalidOrderForPaymentException;
import com.ecommerce.paymentservice.exception.InvalidPaymentStatusTransitionException;
import com.ecommerce.paymentservice.exception.PaymentAlreadyExistsException;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.exception.PaymentOrderMismatchException;
import com.ecommerce.paymentservice.exception.PaymentOrderNotFoundException;
import com.ecommerce.paymentservice.exception.PaymentOrderUnavailableException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderLookupService orderLookupService;

    @Mock
    private PaymentProperties paymentProperties;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        authenticate(1L, "user@example.com", "USER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPayment_shouldPersistWithServerControlledFields() {
        when(orderLookupService.getOrder(10L))
                .thenReturn(new OrderDto(10L, 1L, new BigDecimal("150.00"), "PENDING"));
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(paymentProperties.currency()).thenReturn("PKR");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(100L);
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            return payment;
        });

        PaymentResponse response = paymentService.createPayment(
                new CreatePaymentRequest(10L, PaymentMethod.CASH_ON_DELIVERY));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getAmount()).isEqualByComparingTo("150.00");
        assertThat(saved.getCurrency()).isEqualTo("PKR");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getPaymentReference()).startsWith("PAY-");
        assertThat(response.id()).isEqualTo(100L);
    }

    @Test
    void createPayment_shouldRejectWhenOrderNotFound() {
        when(orderLookupService.getOrder(99L)).thenThrow(new PaymentOrderNotFoundException(99L));

        assertThatThrownBy(() -> paymentService.createPayment(
                new CreatePaymentRequest(99L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentOrderNotFoundException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_shouldRejectWhenOrderServiceUnavailable() {
        when(orderLookupService.getOrder(10L))
                .thenThrow(new PaymentOrderUnavailableException("down"));

        assertThatThrownBy(() -> paymentService.createPayment(
                new CreatePaymentRequest(10L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentOrderUnavailableException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_shouldRejectOtherUsersOrder() {
        when(orderLookupService.getOrder(10L))
                .thenReturn(new OrderDto(10L, 99L, new BigDecimal("10.00"), "PENDING"));

        assertThatThrownBy(() -> paymentService.createPayment(
                new CreatePaymentRequest(10L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentOrderMismatchException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_shouldRejectCancelledOrder() {
        when(orderLookupService.getOrder(10L))
                .thenReturn(new OrderDto(10L, 1L, new BigDecimal("10.00"), "CANCELLED"));

        assertThatThrownBy(() -> paymentService.createPayment(
                new CreatePaymentRequest(10L, PaymentMethod.CARD)))
                .isInstanceOf(InvalidOrderForPaymentException.class);
    }

    @Test
    void createPayment_shouldRejectDeliveredOrder() {
        when(orderLookupService.getOrder(10L))
                .thenReturn(new OrderDto(10L, 1L, new BigDecimal("10.00"), "DELIVERED"));

        assertThatThrownBy(() -> paymentService.createPayment(
                new CreatePaymentRequest(10L, PaymentMethod.CARD)))
                .isInstanceOf(InvalidOrderForPaymentException.class);
    }

    @Test
    void createPayment_shouldRejectDuplicateActivePayment() {
        when(orderLookupService.getOrder(10L))
                .thenReturn(new OrderDto(10L, 1L, new BigDecimal("10.00"), "PENDING"));
        when(paymentRepository.existsByOrderIdAndStatusIn(
                eq(10L),
                eq(EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.PAID))))
                .thenReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(
                new CreatePaymentRequest(10L, PaymentMethod.BANK_TRANSFER)))
                .isInstanceOf(PaymentAlreadyExistsException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_shouldAllowRetryAfterFailedPayment() {
        when(orderLookupService.getOrder(10L))
                .thenReturn(new OrderDto(10L, 1L, new BigDecimal("25.50"), "CONFIRMED"));
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(paymentProperties.currency()).thenReturn("PKR");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(101L);
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            return payment;
        });

        PaymentResponse response = paymentService.createPayment(
                new CreatePaymentRequest(10L, PaymentMethod.CARD));

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void getPayment_shouldReturnOwnedPayment() {
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment(5L, 1L, PaymentStatus.PENDING)));

        PaymentResponse response = paymentService.getPaymentById(5L);

        assertThat(response.userId()).isEqualTo(1L);
    }

    @Test
    void getPayment_shouldForbidOtherUsersPayment() {
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment(5L, 99L, PaymentStatus.PENDING)));

        assertThatThrownBy(() -> paymentService.getPaymentById(5L))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void getPayment_adminCanViewAny() {
        authenticate(2L, "admin@example.com", "ADMIN");
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment(5L, 99L, PaymentStatus.PENDING)));

        assertThat(paymentService.getPaymentById(5L).userId()).isEqualTo(99L);
    }

    @Test
    void listPayments_shouldForbidNonAdmin() {
        assertThatThrownBy(() -> paymentService.listPayments())
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void updateStatus_shouldAllowValidTransitions() {
        authenticate(2L, "admin@example.com", "ADMIN");
        Payment payment = payment(8L, 1L, PaymentStatus.PENDING);
        when(paymentRepository.findById(8L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(paymentService.updateStatus(8L, new UpdatePaymentStatusRequest(PaymentStatus.PROCESSING)).status())
                .isEqualTo(PaymentStatus.PROCESSING);

        payment.setStatus(PaymentStatus.PROCESSING);
        assertThat(paymentService.updateStatus(8L, new UpdatePaymentStatusRequest(PaymentStatus.PAID)).status())
                .isEqualTo(PaymentStatus.PAID);

        payment.setStatus(PaymentStatus.PAID);
        assertThat(paymentService.updateStatus(8L, new UpdatePaymentStatusRequest(PaymentStatus.REFUNDED)).status())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void updateStatus_shouldAllowPendingToFailedAndProcessingToFailed() {
        authenticate(2L, "admin@example.com", "ADMIN");
        Payment payment = payment(8L, 1L, PaymentStatus.PENDING);
        when(paymentRepository.findById(8L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(paymentService.updateStatus(8L, new UpdatePaymentStatusRequest(PaymentStatus.FAILED)).status())
                .isEqualTo(PaymentStatus.FAILED);

        payment.setStatus(PaymentStatus.PROCESSING);
        assertThat(paymentService.updateStatus(8L, new UpdatePaymentStatusRequest(PaymentStatus.FAILED)).status())
                .isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void updateStatus_shouldRejectInvalidTransitions() {
        authenticate(2L, "admin@example.com", "ADMIN");
        when(paymentRepository.findById(8L)).thenReturn(Optional.of(payment(8L, 1L, PaymentStatus.PAID)));

        assertThatThrownBy(() -> paymentService.updateStatus(8L, new UpdatePaymentStatusRequest(PaymentStatus.PENDING)))
                .isInstanceOf(InvalidPaymentStatusTransitionException.class);
        assertThatThrownBy(() -> paymentService.updateStatus(8L, new UpdatePaymentStatusRequest(PaymentStatus.FAILED)))
                .isInstanceOf(InvalidPaymentStatusTransitionException.class);

        when(paymentRepository.findById(9L)).thenReturn(Optional.of(payment(9L, 1L, PaymentStatus.FAILED)));
        assertThatThrownBy(() -> paymentService.updateStatus(9L, new UpdatePaymentStatusRequest(PaymentStatus.PAID)))
                .isInstanceOf(InvalidPaymentStatusTransitionException.class);

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment(10L, 1L, PaymentStatus.REFUNDED)));
        assertThatThrownBy(() -> paymentService.updateStatus(10L, new UpdatePaymentStatusRequest(PaymentStatus.PAID)))
                .isInstanceOf(InvalidPaymentStatusTransitionException.class);
    }

    @Test
    void updateStatus_shouldForbidNonAdmin() {
        assertThatThrownBy(() -> paymentService.updateStatus(8L, new UpdatePaymentStatusRequest(PaymentStatus.PROCESSING)))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void getPayment_shouldThrowWhenMissing() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(404L))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void listMyPayments_shouldReturnCurrentUserPayments() {
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(payment(1L, 1L, PaymentStatus.PENDING)));

        assertThat(paymentService.listMyPayments()).hasSize(1);
    }

    private void authenticate(Long userId, String email, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, email, role, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Payment payment(Long id, Long userId, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setPaymentReference("PAY-TEST" + id);
        payment.setOrderId(10L);
        payment.setUserId(userId);
        payment.setAmount(new BigDecimal("10.00"));
        payment.setCurrency("PKR");
        payment.setMethod(PaymentMethod.CARD);
        payment.setStatus(status);
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        return payment;
    }
}
