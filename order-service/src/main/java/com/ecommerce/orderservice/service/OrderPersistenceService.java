package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.ProductDto;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.entity.Order;
import com.ecommerce.orderservice.entity.OrderItem;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.event.OrderEventTypes;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.mapper.OrderMapper;
import com.ecommerce.orderservice.outbox.OutboxEventWriter;
import com.ecommerce.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OutboxEventWriter outboxEventWriter;

    public OrderPersistenceService(OrderRepository orderRepository, OutboxEventWriter outboxEventWriter) {
        this.orderRepository = orderRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public OrderResponse saveNewOrder(Long userId, String shippingAddress, List<OrderService.PreparedItem> preparedItems) {
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(shippingAddress);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderService.PreparedItem prepared : preparedItems) {
            ProductDto product = prepared.product();
            BigDecimal unitPrice = product.price().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(prepared.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            OrderItem item = new OrderItem();
            item.setProductId(product.id());
            item.setProductNameSnapshot(product.name());
            item.setUnitPriceSnapshot(unitPrice);
            item.setQuantity(prepared.quantity());
            item.setSubtotal(subtotal);
            order.addItem(item);
            total = total.add(subtotal);
        }
        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        Order saved = orderRepository.save(order);
        enqueueStatusEvent(saved, OrderEventTypes.ORDER_CREATED);
        return OrderMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Order getOrderWithItems(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.getItems().size(); // initialize lazy collection inside transaction
        return order;
    }

    @Transactional
    public OrderResponse markCancelled(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        enqueueStatusEvent(saved, OrderEventTypes.ORDER_CANCELLED);
        return OrderMapper.toResponse(saved);
    }

    @Transactional
    public OrderResponse updateStatusAndPublish(Order order, OrderStatus newStatus) {
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        enqueueStatusEvent(saved, mapStatusToEventType(newStatus));
        return OrderMapper.toResponse(saved);
    }

    private void enqueueStatusEvent(Order order, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("status", order.getStatus().name());
        payload.put("totalAmount", order.getTotalAmount());
        payload.put("shippingAddress", order.getShippingAddress());
        outboxEventWriter.enqueueOrderEvent(
                eventType,
                String.valueOf(order.getId()),
                String.valueOf(order.getUserId()),
                payload);
    }

    private static String mapStatusToEventType(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> OrderEventTypes.ORDER_CONFIRMED;
            case PROCESSING -> OrderEventTypes.ORDER_PROCESSING;
            case SHIPPED -> OrderEventTypes.ORDER_SHIPPED;
            case DELIVERED -> OrderEventTypes.ORDER_DELIVERED;
            case CANCELLED -> OrderEventTypes.ORDER_CANCELLED;
            case PENDING -> OrderEventTypes.ORDER_CREATED;
        };
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
