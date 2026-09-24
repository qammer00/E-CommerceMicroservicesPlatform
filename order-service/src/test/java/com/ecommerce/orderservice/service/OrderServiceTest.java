package com.ecommerce.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.orderservice.client.ProductCatalogService;
import com.ecommerce.orderservice.client.ProductDto;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.dto.OrderItemResponse;
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
import com.ecommerce.orderservice.exception.ProductNotFoundException;
import com.ecommerce.orderservice.exception.ProductServiceUnavailableException;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderPersistenceService orderPersistenceService;

    @Mock
    private ProductCatalogService productCatalogService;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        authenticate(1L, "user@example.com", "USER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrder_shouldPersistOrderWithSnapshots() {
        ProductDto product = product("p1", "Laptop", "999.99", 10, true);
        when(productCatalogService.getProduct("p1")).thenReturn(product);
        when(productCatalogService.reserveStock("p1", 2)).thenReturn(product);

        OrderResponse expected = sampleResponse(10L, 1L, OrderStatus.PENDING,
                List.of(new OrderItemResponse(1L, "p1", "Laptop", new BigDecimal("999.99"), 2, new BigDecimal("1999.98"))));
        when(orderPersistenceService.saveNewOrder(eq(1L), eq("123 Main St"), anyList())).thenReturn(expected);

        OrderResponse response = orderService.createOrder(new CreateOrderRequest(
                List.of(new OrderItemRequest("p1", 2)),
                "123 Main St"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.totalAmount()).isEqualByComparingTo("1999.98");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().productNameSnapshot()).isEqualTo("Laptop");
        verify(productCatalogService).reserveStock("p1", 2);
        verify(orderPersistenceService).saveNewOrder(eq(1L), eq("123 Main St"), anyList());
    }

    @Test
    void createOrder_shouldSupportMultipleItems() {
        ProductDto p1 = product("p1", "Laptop", "100.00", 5, true);
        ProductDto p2 = product("p2", "Mouse", "20.00", 50, true);
        when(productCatalogService.getProduct("p1")).thenReturn(p1);
        when(productCatalogService.getProduct("p2")).thenReturn(p2);
        when(productCatalogService.reserveStock("p1", 1)).thenReturn(p1);
        when(productCatalogService.reserveStock("p2", 3)).thenReturn(p2);

        OrderResponse expected = sampleResponse(11L, 1L, OrderStatus.PENDING, List.of(
                new OrderItemResponse(1L, "p1", "Laptop", new BigDecimal("100.00"), 1, new BigDecimal("100.00")),
                new OrderItemResponse(2L, "p2", "Mouse", new BigDecimal("20.00"), 3, new BigDecimal("60.00"))));
        when(orderPersistenceService.saveNewOrder(eq(1L), anyString(), anyList())).thenReturn(expected);

        OrderResponse response = orderService.createOrder(new CreateOrderRequest(
                List.of(new OrderItemRequest("p1", 1), new OrderItemRequest("p2", 3)),
                "Ship me"));

        assertThat(response.items()).hasSize(2);
        verify(productCatalogService).reserveStock("p1", 1);
        verify(productCatalogService).reserveStock("p2", 3);
    }

    @Test
    void createOrder_shouldRejectInsufficientStock() {
        when(productCatalogService.getProduct("p1"))
                .thenReturn(product("p1", "Laptop", "10.00", 1, true));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                List.of(new OrderItemRequest("p1", 5)), "addr")))
                .isInstanceOf(InsufficientStockException.class);

        verify(productCatalogService, never()).reserveStock(anyString(), anyInt());
        verify(orderPersistenceService, never()).saveNewOrder(anyLong(), anyString(), anyList());
    }

    @Test
    void createOrder_shouldRejectInactiveProduct() {
        when(productCatalogService.getProduct("p1"))
                .thenReturn(product("p1", "Laptop", "10.00", 100, false));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                List.of(new OrderItemRequest("p1", 1)), "addr")))
                .isInstanceOf(InactiveProductException.class);

        verify(orderPersistenceService, never()).saveNewOrder(anyLong(), anyString(), anyList());
    }

    @Test
    void createOrder_shouldRejectProductNotFound() {
        when(productCatalogService.getProduct("missing"))
                .thenThrow(new ProductNotFoundException("missing"));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                List.of(new OrderItemRequest("missing", 1)), "addr")))
                .isInstanceOf(ProductNotFoundException.class);

        verify(orderPersistenceService, never()).saveNewOrder(anyLong(), anyString(), anyList());
    }

    @Test
    void createOrder_shouldNotCreateWhenProductServiceUnavailable() {
        when(productCatalogService.getProduct("p1"))
                .thenThrow(new ProductServiceUnavailableException("down"));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                List.of(new OrderItemRequest("p1", 1)), "addr")))
                .isInstanceOf(ProductServiceUnavailableException.class);

        verify(orderPersistenceService, never()).saveNewOrder(anyLong(), anyString(), anyList());
    }

    @Test
    void createOrder_shouldCompensateWhenSecondReserveFails() {
        ProductDto p1 = product("p1", "A", "10.00", 5, true);
        ProductDto p2 = product("p2", "B", "10.00", 5, true);
        when(productCatalogService.getProduct("p1")).thenReturn(p1);
        when(productCatalogService.getProduct("p2")).thenReturn(p2);
        when(productCatalogService.reserveStock("p1", 1)).thenReturn(p1);
        when(productCatalogService.reserveStock("p2", 1))
                .thenThrow(new InsufficientStockException("p2", 1));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                List.of(new OrderItemRequest("p1", 1), new OrderItemRequest("p2", 1)), "addr")))
                .isInstanceOf(InsufficientStockException.class);

        verify(productCatalogService).releaseStock("p1", 1);
        verify(orderPersistenceService, never()).saveNewOrder(anyLong(), anyString(), anyList());
    }

    @Test
    void getOrderById_shouldReturnOwnedOrder() {
        Order order = ownedOrder(5L, 1L, OrderStatus.PENDING);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.userId()).isEqualTo(1L);
    }

    @Test
    void getOrderById_shouldForbidOtherUsersOrder() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(ownedOrder(5L, 99L, OrderStatus.PENDING)));

        assertThatThrownBy(() -> orderService.getOrderById(5L))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void getOrderById_adminCanViewAnyOrder() {
        authenticate(2L, "admin@example.com", "ADMIN");
        when(orderRepository.findById(5L)).thenReturn(Optional.of(ownedOrder(5L, 99L, OrderStatus.PENDING)));

        OrderResponse response = orderService.getOrderById(5L);

        assertThat(response.userId()).isEqualTo(99L);
    }

    @Test
    void listMyOrders_shouldReturnCurrentUserOrders() {
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(ownedOrder(1L, 1L, OrderStatus.PENDING)));

        List<OrderResponse> orders = orderService.listMyOrders();

        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().userId()).isEqualTo(1L);
    }

    @Test
    void listOrders_shouldForbidNonAdmin() {
        assertThatThrownBy(() -> orderService.listOrders())
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void cancelOrder_shouldCancelOwnedPendingOrder() {
        Order order = ownedOrder(7L, 1L, OrderStatus.PENDING);
        when(orderPersistenceService.getOrderWithItems(7L)).thenReturn(order);
        when(orderPersistenceService.markCancelled(7L))
                .thenReturn(sampleResponse(7L, 1L, OrderStatus.CANCELLED, List.of()));

        OrderResponse response = orderService.cancelOrder(7L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(productCatalogService).releaseStock("p1", 2);
    }

    @Test
    void cancelOrder_shouldRejectAfterShipment() {
        when(orderPersistenceService.getOrderWithItems(7L))
                .thenReturn(ownedOrder(7L, 1L, OrderStatus.SHIPPED));

        assertThatThrownBy(() -> orderService.cancelOrder(7L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        verify(orderPersistenceService, never()).markCancelled(anyLong());
    }

    @Test
    void updateStatus_shouldAllowValidTransitionForAdmin() {
        authenticate(2L, "admin@example.com", "ADMIN");
        Order order = ownedOrder(8L, 1L, OrderStatus.PENDING);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));
        when(orderPersistenceService.updateStatusAndPublish(any(Order.class), eq(OrderStatus.CONFIRMED)))
                .thenReturn(sampleResponse(8L, 1L, OrderStatus.CONFIRMED, List.of()));

        OrderResponse response = orderService.updateStatus(8L, new UpdateOrderStatusRequest(OrderStatus.CONFIRMED));

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderPersistenceService).updateStatusAndPublish(any(Order.class), eq(OrderStatus.CONFIRMED));
    }

    @Test
    void updateStatus_shouldRejectInvalidTransition() {
        authenticate(2L, "admin@example.com", "ADMIN");
        when(orderRepository.findById(8L)).thenReturn(Optional.of(ownedOrder(8L, 1L, OrderStatus.PENDING)));

        assertThatThrownBy(() -> orderService.updateStatus(8L, new UpdateOrderStatusRequest(OrderStatus.SHIPPED)))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void updateStatus_shouldForbidNonAdmin() {
        assertThatThrownBy(() -> orderService.updateStatus(8L, new UpdateOrderStatusRequest(OrderStatus.CONFIRMED)))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void getOrderById_shouldThrowWhenMissing() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(404L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private void authenticate(Long userId, String email, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                email,
                role,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ProductDto product(String id, String name, String price, int stock, boolean active) {
        return new ProductDto(id, name, new BigDecimal(price), stock, active);
    }

    private Order ownedOrder(Long id, Long userId, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderNumber("ORD-TEST" + id);
        order.setTotalAmount(new BigDecimal("1999.98"));
        order.setStatus(status);
        order.setShippingAddress("123 Main St");
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setProductId("p1");
        item.setProductNameSnapshot("Laptop");
        item.setUnitPriceSnapshot(new BigDecimal("999.99"));
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("1999.98"));
        order.addItem(item);
        return order;
    }

    private OrderResponse sampleResponse(Long id, Long userId, OrderStatus status, List<OrderItemResponse> items) {
        return new OrderResponse(
                id,
                userId,
                "ORD-TEST" + id,
                items.stream()
                        .map(OrderItemResponse::subtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                status,
                "123 Main St",
                items,
                Instant.now(),
                Instant.now());
    }
}
