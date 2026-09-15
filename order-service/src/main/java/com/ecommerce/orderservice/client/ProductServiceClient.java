package com.ecommerce.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Discovers product-service via Eureka ({@code name = "product-service"}).
 * Do not hardcode localhost URLs.
 */
@FeignClient(name = "product-service")
public interface ProductServiceClient {

    @GetMapping("/api/products/{id}")
    ProductApiResponse<ProductDto> getProduct(@PathVariable("id") String id);

    @PostMapping("/api/products/{id}/stock/reserve")
    ProductApiResponse<ProductDto> reserveStock(
            @PathVariable("id") String id,
            @RequestBody ReserveStockRequest request);

    @PostMapping("/api/products/{id}/stock/release")
    ProductApiResponse<ProductDto> releaseStock(
            @PathVariable("id") String id,
            @RequestBody ReserveStockRequest request);
}
