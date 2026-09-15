package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.ProductCatalogService;
import com.ecommerce.orderservice.client.ProductDto;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.dto.UpdateOrderStatusRequest;
import com.ecommerce.orderservice.entity.Order;
import com.ecommerce.orderservice.entity.OrderItem;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.exception.ForbiddenAccessException;
import com.ecommerce.orderservice.exception.InactiveProductException;
import com.ecommerce.orderservice.exception.InsufficientStockException;
import com.ecommerce.orderservice.exception.InvalidOrderStatusTransitionException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.mapper.OrderMapper;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.security.AuthenticatedUser;
import com.ecommerce.orderservice.security.SecurityUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;
    private final OrderPersistenceService orderPersistenceService;
    private final ProductCatalogService productCatalogService;

    public OrderService(
            OrderRepository orderRepository,
            OrderPersistenceService orderPersistenceService,
            ProductCatalogService productCatalogService) {
        this.orderRepository = orderRepository;
        this.orderPersistenceService = orderPersistenceService;
        this.productCatalogService = productCatalogService;
    }

    /**
     * Product Service calls happen outside the local DB transaction.
     * Only Order Service MySQL writes are transactional; stock reservation is compensated on failure.
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        List<ReservedItem> reservedItems = new ArrayList<>();

        try {
            List<PreparedItem> preparedItems = new ArrayList<>();
            for (OrderItemRequest itemRequest : request.items()) {
                ProductDto product = productCatalogService.getProduct(itemRequest.productId());
                validateProductForOrder(product, itemRequest.quantity());
                preparedItems.add(new PreparedItem(product, itemRequest.quantity()));
            }

            for (PreparedItem prepared : preparedItems) {
                productCatalogService.reserveStock(prepared.product().id(), prepared.quantity());
                reservedItems.add(new ReservedItem(prepared.product().id(), prepared.quantity()));
            }

            return orderPersistenceService.saveNewOrder(
                    currentUser.getUserId(), request.shippingAddress(), preparedItems);
        } catch (RuntimeException ex) {
            compensateReservations(reservedItems);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        Order order = getOrderOrThrow(id);
        assertCanView(currentUser, order);
        return OrderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders() {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        if (!SecurityUtils.isAdmin(currentUser)) {
            throw new ForbiddenAccessException("Only administrators can list all orders");
        }
        return orderRepository.findAll().stream().map(OrderMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMyOrders() {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        return orderRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(OrderMapper::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        if (!SecurityUtils.isAdmin(currentUser)) {
            throw new ForbiddenAccessException("Only administrators can update order status");
        }
        Order order = getOrderOrThrow(id);
        assertValidTransition(order.getStatus(), request.status());
        order.setStatus(request.status());
        return OrderMapper.toResponse(orderRepository.save(order));
    }

    public OrderResponse cancelOrder(Long id) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        Order order = orderPersistenceService.getOrderWithItems(id);
        if (!SecurityUtils.isAdmin(currentUser) && !order.getUserId().equals(currentUser.getUserId())) {
            throw new ForbiddenAccessException("You are not allowed to cancel this order");
        }
        if (!canCancel(order.getStatus())) {
            throw new InvalidOrderStatusTransitionException(order.getStatus(), OrderStatus.CANCELLED);
        }

        List<OrderItem> items = order.getItems().stream()
                .map(item -> {
                    OrderItem copy = new OrderItem();
                    copy.setProductId(item.getProductId());
                    copy.setQuantity(item.getQuantity());
                    return copy;
                })
                .toList();
        OrderResponse response = orderPersistenceService.markCancelled(order.getId());

        for (OrderItem item : items) {
            try {
                productCatalogService.releaseStock(item.getProductId(), item.getQuantity());
            } catch (Exception ex) {
                log.warn("Failed to release stock for product {} while cancelling order {}: {}",
                        item.getProductId(), id, ex.getMessage());
            }
        }
        return response;
    }

    private void validateProductForOrder(ProductDto product, int quantity) {
        if (!product.active()) {
            throw new InactiveProductException(product.id());
        }
        int available = product.stockQuantity() == null ? 0 : product.stockQuantity();
        if (available < quantity) {
            throw new InsufficientStockException(product.id(), quantity);
        }
    }

    private void assertCanView(AuthenticatedUser currentUser, Order order) {
        if (!SecurityUtils.isAdmin(currentUser) && !order.getUserId().equals(currentUser.getUserId())) {
            throw new ForbiddenAccessException("You are not allowed to view this order");
        }
    }

    private void assertValidTransition(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(OrderStatus.class));
        if (!allowed.contains(to)) {
            throw new InvalidOrderStatusTransitionException(from, to);
        }
    }

    private boolean canCancel(OrderStatus status) {
        return status == OrderStatus.PENDING
                || status == OrderStatus.CONFIRMED
                || status == OrderStatus.PROCESSING;
    }

    private Order getOrderOrThrow(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private void compensateReservations(List<ReservedItem> reservedItems) {
        for (ReservedItem reserved : reservedItems) {
            try {
                productCatalogService.releaseStock(reserved.productId(), reserved.quantity());
            } catch (Exception ex) {
                log.warn("Failed to compensate reserved stock for product {}: {}",
                        reserved.productId(), ex.getMessage());
            }
        }
    }

    public record PreparedItem(ProductDto product, int quantity) {
    }

    private record ReservedItem(String productId, int quantity) {
    }
}
