package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.client.OrderDto;
import com.ecommerce.paymentservice.client.OrderLookupService;
import com.ecommerce.paymentservice.config.PaymentProperties;
import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.dto.UpdatePaymentStatusRequest;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.entity.PaymentStatus;
import com.ecommerce.paymentservice.exception.ForbiddenAccessException;
import com.ecommerce.paymentservice.exception.InvalidOrderForPaymentException;
import com.ecommerce.paymentservice.exception.InvalidPaymentStatusTransitionException;
import com.ecommerce.paymentservice.exception.PaymentAlreadyExistsException;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.exception.PaymentOrderMismatchException;
import com.ecommerce.paymentservice.mapper.PaymentMapper;
import com.ecommerce.paymentservice.outbox.OutboxEventWriter;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.security.AuthenticatedUser;
import com.ecommerce.paymentservice.security.SecurityUtils;
import com.ecommerce.paymentservice.event.PaymentEventTypes;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Set<PaymentStatus> ACTIVE_STATUSES =
            EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.PAID);

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PROCESSING, EnumSet.of(PaymentStatus.PAID, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PAID, EnumSet.of(PaymentStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    private final PaymentRepository paymentRepository;
    private final OrderLookupService orderLookupService;
    private final PaymentProperties paymentProperties;
    private final OutboxEventWriter outboxEventWriter;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderLookupService orderLookupService,
            PaymentProperties paymentProperties,
            OutboxEventWriter outboxEventWriter) {
        this.paymentRepository = paymentRepository;
        this.orderLookupService = orderLookupService;
        this.paymentProperties = paymentProperties;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        OrderDto order = orderLookupService.getOrder(request.orderId());

        if (order.userId() == null || !order.userId().equals(currentUser.getUserId())) {
            throw new PaymentOrderMismatchException(
                    "Order " + request.orderId() + " does not belong to the authenticated user");
        }

        String orderStatus = order.status() == null ? "" : order.status().toUpperCase();
        if ("CANCELLED".equals(orderStatus)) {
            throw new InvalidOrderForPaymentException("Cannot create payment for a CANCELLED order");
        }
        if ("DELIVERED".equals(orderStatus)) {
            throw new InvalidOrderForPaymentException("Cannot create payment for a DELIVERED order");
        }

        if (paymentRepository.existsByOrderIdAndStatusIn(request.orderId(), ACTIVE_STATUSES)) {
            throw new PaymentAlreadyExistsException(request.orderId());
        }

        if (order.totalAmount() == null) {
            throw new InvalidOrderForPaymentException("Order total amount is missing");
        }

        Payment payment = new Payment();
        payment.setPaymentReference(generatePaymentReference());
        payment.setOrderId(order.id());
        payment.setUserId(currentUser.getUserId());
        payment.setAmount(order.totalAmount().setScale(2, RoundingMode.HALF_UP));
        payment.setCurrency(paymentProperties.currency() == null ? "PKR" : paymentProperties.currency());
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.PENDING);

        Payment saved = paymentRepository.save(payment);
        enqueuePaymentEvent(saved, PaymentEventTypes.PAYMENT_CREATED);
        return PaymentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        Payment payment = getPaymentOrThrow(id);
        assertCanView(currentUser, payment);
        return PaymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listMyPayments() {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(PaymentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments() {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        if (!SecurityUtils.isAdmin(currentUser)) {
            throw new ForbiddenAccessException("Only administrators can list all payments");
        }
        return paymentRepository.findAll().stream().map(PaymentMapper::toResponse).toList();
    }

    @Transactional
    public PaymentResponse updateStatus(Long id, UpdatePaymentStatusRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        if (!SecurityUtils.isAdmin(currentUser)) {
            throw new ForbiddenAccessException("Only administrators can update payment status");
        }
        Payment payment = getPaymentOrThrow(id);
        assertValidTransition(payment.getStatus(), request.status());
        payment.setStatus(request.status());
        Payment saved = paymentRepository.save(payment);
        String eventType = mapStatusToEventType(request.status());
        if (eventType != null) {
            enqueuePaymentEvent(saved, eventType);
        }
        return PaymentMapper.toResponse(saved);
    }

    private void enqueuePaymentEvent(Payment payment, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentReference", payment.getPaymentReference());
        payload.put("orderId", payment.getOrderId());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("method", payment.getMethod().name());
        payload.put("status", payment.getStatus().name());
        outboxEventWriter.enqueuePaymentEvent(
                eventType,
                String.valueOf(payment.getId()),
                String.valueOf(payment.getUserId()),
                payload);
    }

    private static String mapStatusToEventType(PaymentStatus status) {
        return switch (status) {
            case PAID -> PaymentEventTypes.PAYMENT_PAID;
            case FAILED -> PaymentEventTypes.PAYMENT_FAILED;
            case REFUNDED -> PaymentEventTypes.PAYMENT_REFUNDED;
            case PENDING, PROCESSING -> null;
        };
    }

    private void assertCanView(AuthenticatedUser currentUser, Payment payment) {
        if (!SecurityUtils.isAdmin(currentUser) && !payment.getUserId().equals(currentUser.getUserId())) {
            throw new ForbiddenAccessException("You are not allowed to view this payment");
        }
    }

    private void assertValidTransition(PaymentStatus from, PaymentStatus to) {
        Set<PaymentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class));
        if (!allowed.contains(to)) {
            throw new InvalidPaymentStatusTransitionException(from, to);
        }
    }

    private Payment getPaymentOrThrow(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private String generatePaymentReference() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
