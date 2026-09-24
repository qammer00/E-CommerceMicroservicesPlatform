package com.ecommerce.paymentservice.client;

import com.ecommerce.paymentservice.exception.InvalidOrderForPaymentException;
import com.ecommerce.paymentservice.exception.PaymentOrderMismatchException;
import com.ecommerce.paymentservice.exception.PaymentOrderNotFoundException;
import com.ecommerce.paymentservice.exception.PaymentOrderUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

@Service
public class OrderLookupService {

    private final OrderServiceClient orderServiceClient;

    public OrderLookupService(OrderServiceClient orderServiceClient) {
        this.orderServiceClient = orderServiceClient;
    }

    @CircuitBreaker(name = "orderService", fallbackMethod = "getOrderFallback")
    @Retry(name = "orderService")
    public OrderDto getOrder(Long orderId) {
        try {
            OrderApiResponse<OrderDto> response = orderServiceClient.getOrder(orderId);
            if (response == null || response.data() == null) {
                throw new PaymentOrderNotFoundException(orderId);
            }
            return response.data();
        } catch (PaymentOrderNotFoundException | PaymentOrderMismatchException | InvalidOrderForPaymentException ex) {
            throw ex;
        } catch (FeignException.NotFound ex) {
            throw new PaymentOrderNotFoundException(orderId);
        } catch (FeignException.Forbidden ex) {
            throw new PaymentOrderMismatchException("You are not allowed to access order " + orderId);
        } catch (FeignException.Unauthorized ex) {
            throw new PaymentOrderUnavailableException(
                    "Order Service rejected authentication while fetching order " + orderId, ex);
        } catch (FeignException ex) {
            throw new PaymentOrderUnavailableException(
                    "Order Service error while fetching order " + orderId + " (HTTP " + ex.status() + ")",
                    ex);
        }
    }

    @SuppressWarnings("unused")
    private OrderDto getOrderFallback(Long orderId, Throwable cause) {
        if (cause instanceof PaymentOrderNotFoundException
                || cause instanceof PaymentOrderMismatchException
                || cause instanceof InvalidOrderForPaymentException) {
            throw (RuntimeException) cause;
        }
        throw new PaymentOrderUnavailableException(
                "Order Service is unavailable while fetching order " + orderId, cause);
    }
}
