package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.ApiResponse;
import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.ProductPageResponse;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.dto.ReserveStockRequest;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.dto.UpdateProductStatusRequest;
import com.ecommerce.productservice.dto.UpdateStockRequest;
import com.ecommerce.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product catalog APIs")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "Create a product")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            HttpServletRequest httpRequest) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Product created successfully",
                product,
                requestId(httpRequest)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by id")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(
                "Product retrieved successfully",
                product,
                requestId(httpRequest)));
    }

    @GetMapping
    @Operation(summary = "List products with pagination, sorting, and filters")
    public ResponseEntity<ApiResponse<ProductPageResponse>> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String name,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest httpRequest) {
        ProductPageResponse page = productService.listProducts(category, active, name, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                "Products retrieved successfully",
                page,
                requestId(httpRequest)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request,
            HttpServletRequest httpRequest) {
        ProductResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Product updated successfully",
                product,
                requestId(httpRequest)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(
                "Product deleted successfully",
                null,
                requestId(httpRequest)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update product active status")
    public ResponseEntity<ApiResponse<ProductResponse>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductStatusRequest request,
            HttpServletRequest httpRequest) {
        ProductResponse product = productService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Product status updated successfully",
                product,
                requestId(httpRequest)));
    }

    @PatchMapping("/{id}/stock")
    @Operation(summary = "Update product stock quantity")
    public ResponseEntity<ApiResponse<ProductResponse>> updateStock(
            @PathVariable String id,
            @Valid @RequestBody UpdateStockRequest request,
            HttpServletRequest httpRequest) {
        ProductResponse product = productService.updateStock(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Product stock updated successfully",
                product,
                requestId(httpRequest)));
    }

    @PostMapping("/{id}/stock/reserve")
    @Operation(summary = "Reserve (decrease) product stock for an order")
    public ResponseEntity<ApiResponse<ProductResponse>> reserveStock(
            @PathVariable String id,
            @Valid @RequestBody ReserveStockRequest request,
            HttpServletRequest httpRequest) {
        ProductResponse product = productService.reserveStock(id, request.quantity());
        return ResponseEntity.ok(ApiResponse.success(
                "Product stock reserved successfully",
                product,
                requestId(httpRequest)));
    }

    @PostMapping("/{id}/stock/release")
    @Operation(summary = "Release previously reserved product stock")
    public ResponseEntity<ApiResponse<ProductResponse>> releaseStock(
            @PathVariable String id,
            @Valid @RequestBody ReserveStockRequest request,
            HttpServletRequest httpRequest) {
        ProductResponse product = productService.releaseStock(id, request.quantity());
        return ResponseEntity.ok(ApiResponse.success(
                "Product stock released successfully",
                product,
                requestId(httpRequest)));
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString();
    }
}
