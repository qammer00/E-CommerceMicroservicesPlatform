package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.exception.InactiveProductException;
import com.ecommerce.orderservice.exception.InsufficientStockException;
import com.ecommerce.orderservice.exception.ProductNotFoundException;
import com.ecommerce.orderservice.exception.ProductServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

@Service
public class ProductCatalogService {

    private final ProductServiceClient productServiceClient;

    public ProductCatalogService(ProductServiceClient productServiceClient) {
        this.productServiceClient = productServiceClient;
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    @Retry(name = "productService")
    public ProductDto getProduct(String productId) {
        try {
            ProductApiResponse<ProductDto> response = productServiceClient.getProduct(productId);
            if (response == null || response.data() == null) {
                throw new ProductNotFoundException(productId);
            }
            return response.data();
        } catch (ProductNotFoundException | InactiveProductException | InsufficientStockException ex) {
            throw ex;
        } catch (FeignException.NotFound ex) {
            throw new ProductNotFoundException(productId);
        } catch (FeignException ex) {
            throw new ProductServiceUnavailableException(
                    "Product Service error while fetching product " + productId + " (HTTP " + ex.status() + ")",
                    ex);
        }
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "reserveStockFallback")
    @Retry(name = "productService")
    public ProductDto reserveStock(String productId, int quantity) {
        try {
            ProductApiResponse<ProductDto> response =
                    productServiceClient.reserveStock(productId, new ReserveStockRequest(quantity));
            if (response == null || response.data() == null) {
                throw new ProductServiceUnavailableException(
                        "Product Service returned an empty reserve response for product " + productId);
            }
            return response.data();
        } catch (ProductNotFoundException | InactiveProductException | InsufficientStockException ex) {
            throw ex;
        } catch (FeignException.NotFound ex) {
            throw new ProductNotFoundException(productId);
        } catch (FeignException.Conflict ex) {
            throw new InsufficientStockException(productId, quantity);
        } catch (FeignException.BadRequest ex) {
            String body = ex.contentUTF8();
            if (body != null && body.toLowerCase().contains("inactive")) {
                throw new InactiveProductException(productId);
            }
            throw new InsufficientStockException(productId, quantity);
        } catch (FeignException ex) {
            throw new ProductServiceUnavailableException(
                    "Product Service error while reserving stock for product " + productId
                            + " (HTTP " + ex.status() + ")",
                    ex);
        }
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "releaseStockFallback")
    public void releaseStock(String productId, int quantity) {
        try {
            productServiceClient.releaseStock(productId, new ReserveStockRequest(quantity));
        } catch (Exception ex) {
            throw new ProductServiceUnavailableException(
                    "Failed to release stock for product " + productId + " after order failure", ex);
        }
    }

    @SuppressWarnings("unused")
    private ProductDto getProductFallback(String productId, Throwable cause) {
        if (cause instanceof ProductNotFoundException
                || cause instanceof InactiveProductException
                || cause instanceof InsufficientStockException) {
            throw (RuntimeException) cause;
        }
        throw new ProductServiceUnavailableException(
                "Product Service is unavailable while fetching product " + productId, cause);
    }

    @SuppressWarnings("unused")
    private ProductDto reserveStockFallback(String productId, int quantity, Throwable cause) {
        if (cause instanceof ProductNotFoundException
                || cause instanceof InactiveProductException
                || cause instanceof InsufficientStockException) {
            throw (RuntimeException) cause;
        }
        throw new ProductServiceUnavailableException(
                "Product Service is unavailable while reserving stock for product " + productId, cause);
    }

    @SuppressWarnings("unused")
    private void releaseStockFallback(String productId, int quantity, Throwable cause) {
        throw new ProductServiceUnavailableException(
                "Product Service is unavailable while releasing stock for product " + productId, cause);
    }
}
