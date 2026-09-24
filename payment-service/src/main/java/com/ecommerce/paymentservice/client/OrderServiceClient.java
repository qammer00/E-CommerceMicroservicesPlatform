package com.ecommerce.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Discovers order-service via Eureka. Do not hardcode localhost URLs.
 * Authorization header is forwarded by {@link FeignAuthRequestInterceptor}.
 */
@FeignClient(name = "order-service")
public interface OrderServiceClient {

    @GetMapping("/api/orders/{id}")
    OrderApiResponse<OrderDto> getOrder(@PathVariable("id") Long id);
}
