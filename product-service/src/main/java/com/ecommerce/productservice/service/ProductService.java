package com.ecommerce.productservice.service;

import com.ecommerce.productservice.document.Product;
import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.ProductPageResponse;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.dto.UpdateProductStatusRequest;
import com.ecommerce.productservice.dto.UpdateStockRequest;
import com.ecommerce.productservice.exception.DuplicateSkuException;
import com.ecommerce.productservice.exception.InsufficientStockException;
import com.ecommerce.productservice.exception.InvalidStockException;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.event.ProductEventTypes;
import com.ecommerce.productservice.kafka.StockEventPublisher;
import com.ecommerce.productservice.repository.ProductRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductService {

    public static final String CACHE_PRODUCTS = "products";
    public static final String CACHE_PRODUCT_LISTS = "productLists";

    private final ProductRepository productRepository;
    private final StockEventPublisher stockEventPublisher;

    public ProductService(ProductRepository productRepository, StockEventPublisher stockEventPublisher) {
        this.productRepository = productRepository;
        this.stockEventPublisher = stockEventPublisher;
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_PRODUCT_LISTS, allEntries = true)
    })
    public ProductResponse createProduct(CreateProductRequest request) {
        String sku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setSku(sku);
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setCategory(request.category());
        product.setImageUrl(request.imageUrl());
        product.setActive(request.active() == null || request.active());
        Instant now = Instant.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        return ProductMapper.toResponse(productRepository.save(product));
    }

    @Cacheable(value = CACHE_PRODUCTS, key = "#id")
    public ProductResponse getProductById(String id) {
        return ProductMapper.toResponse(getProductOrThrow(id));
    }

    @Cacheable(
            value = CACHE_PRODUCT_LISTS,
            key = "T(java.lang.String).format('%s|%s|%s|%d|%d|%s', #category, #active, #name, #pageable.pageNumber, #pageable.pageSize, #pageable.sort)")
    public ProductPageResponse listProducts(String category, Boolean active, String name, Pageable pageable) {
        Page<Product> page = productRepository.search(category, active, name, pageable);
        return new ProductPageResponse(
                page.getContent().stream().map(ProductMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = CACHE_PRODUCT_LISTS, allEntries = true)
    })
    public ProductResponse updateProduct(String id, UpdateProductRequest request) {
        Product product = getProductOrThrow(id);

        if (StringUtils.hasText(request.sku())) {
            String normalizedSku = request.sku().trim().toUpperCase();
            if (productRepository.existsBySkuAndIdNot(normalizedSku, id)) {
                throw new DuplicateSkuException(normalizedSku);
            }
            product.setSku(normalizedSku);
        }
        if (StringUtils.hasText(request.name())) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.stockQuantity() != null) {
            if (request.stockQuantity() < 0) {
                throw new InvalidStockException("Stock quantity must not be negative");
            }
            product.setStockQuantity(request.stockQuantity());
        }
        if (StringUtils.hasText(request.category())) {
            product.setCategory(request.category());
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }
        product.setUpdatedAt(Instant.now());

        return ProductMapper.toResponse(productRepository.save(product));
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = CACHE_PRODUCT_LISTS, allEntries = true)
    })
    public void deleteProduct(String id) {
        Product product = getProductOrThrow(id);
        productRepository.delete(product);
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = CACHE_PRODUCT_LISTS, allEntries = true)
    })
    public ProductResponse updateStatus(String id, UpdateProductStatusRequest request) {
        Product product = getProductOrThrow(id);
        product.setActive(request.active());
        product.setUpdatedAt(Instant.now());
        return ProductMapper.toResponse(productRepository.save(product));
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = CACHE_PRODUCT_LISTS, allEntries = true)
    })
    public ProductResponse updateStock(String id, UpdateStockRequest request) {
        if (request.stockQuantity() < 0) {
            throw new InvalidStockException("Stock quantity must not be negative");
        }
        Product product = getProductOrThrow(id);
        product.setStockQuantity(request.stockQuantity());
        product.setUpdatedAt(Instant.now());
        return ProductMapper.toResponse(productRepository.save(product));
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = CACHE_PRODUCT_LISTS, allEntries = true)
    })
    public ProductResponse reserveStock(String id, int quantity) {
        if (quantity < 1) {
            throw new InvalidStockException("Reserve quantity must be at least 1");
        }
        Product product = getProductOrThrow(id);
        if (!product.isActive()) {
            throw new InvalidStockException("Product is inactive: " + id);
        }
        int available = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        if (available < quantity) {
            throw new InsufficientStockException(id, quantity, available);
        }
        product.setStockQuantity(available - quantity);
        product.setUpdatedAt(Instant.now());
        ProductResponse response = ProductMapper.toResponse(productRepository.save(product));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("quantity", quantity);
        payload.put("remainingStock", response.stockQuantity());
        stockEventPublisher.publish(ProductEventTypes.STOCK_RESERVED, id, payload);
        return response;
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = CACHE_PRODUCT_LISTS, allEntries = true)
    })
    public ProductResponse releaseStock(String id, int quantity) {
        if (quantity < 1) {
            throw new InvalidStockException("Release quantity must be at least 1");
        }
        Product product = getProductOrThrow(id);
        int available = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        product.setStockQuantity(available + quantity);
        product.setUpdatedAt(Instant.now());
        ProductResponse response = ProductMapper.toResponse(productRepository.save(product));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("quantity", quantity);
        payload.put("remainingStock", response.stockQuantity());
        stockEventPublisher.publish(ProductEventTypes.STOCK_RELEASED, id, payload);
        return response;
    }

    private Product getProductOrThrow(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
