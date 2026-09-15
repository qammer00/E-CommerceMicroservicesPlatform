package com.ecommerce.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.productservice.document.Product;
import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.dto.UpdateProductStatusRequest;
import com.ecommerce.productservice.dto.UpdateStockRequest;
import com.ecommerce.productservice.exception.DuplicateSkuException;
import com.ecommerce.productservice.exception.InvalidStockException;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId("prod-1");
        product.setName("Wireless Mouse");
        product.setDescription("Ergonomic mouse");
        product.setSku("MOUSE-001");
        product.setPrice(new BigDecimal("29.99"));
        product.setStockQuantity(100);
        product.setCategory("Electronics");
        product.setImageUrl("https://cdn.example.com/mouse.png");
        product.setActive(true);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
    }

    @Test
    void createProduct_shouldPersistValidProduct() {
        CreateProductRequest request = new CreateProductRequest(
                "Wireless Mouse",
                "Ergonomic mouse",
                "mouse-001",
                new BigDecimal("29.99"),
                100,
                "Electronics",
                "https://cdn.example.com/mouse.png",
                true);

        when(productRepository.existsBySku("MOUSE-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId("prod-1");
            return saved;
        });

        var response = productService.createProduct(request);

        assertThat(response.sku()).isEqualTo("MOUSE-001");
        assertThat(response.price()).isEqualByComparingTo("29.99");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_shouldRejectDuplicateSku() {
        CreateProductRequest request = new CreateProductRequest(
                "Wireless Mouse",
                null,
                "MOUSE-001",
                new BigDecimal("29.99"),
                10,
                "Electronics",
                null,
                true);
        when(productRepository.existsBySku("MOUSE-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void getProductById_shouldReturnProduct() {
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

        var response = productService.getProductById("prod-1");

        assertThat(response.id()).isEqualTo("prod-1");
        assertThat(response.name()).isEqualTo("Wireless Mouse");
    }

    @Test
    void getProductById_shouldThrowWhenMissing() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById("missing"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void listProducts_shouldSupportPaginationAndFilters() {
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(productRepository.search(eq("Electronics"), eq(true), eq("Mouse"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));

        var response = productService.listProducts("Electronics", true, "Mouse", pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.page()).isZero();
    }

    @Test
    void updateProduct_shouldUpdateFields() {
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        var response = productService.updateProduct(
                "prod-1",
                new UpdateProductRequest("New Name", null, null, new BigDecimal("39.99"), null, null, null, null));

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(product.getPrice()).isEqualByComparingTo("39.99");
    }

    @Test
    void deleteProduct_shouldRemoveProduct() {
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));

        productService.deleteProduct("prod-1");

        verify(productRepository).delete(product);
    }

    @Test
    void updateStatus_shouldToggleActive() {
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        var response = productService.updateStatus("prod-1", new UpdateProductStatusRequest(false));

        assertThat(response.active()).isFalse();
    }

    @Test
    void updateStock_shouldRejectNegativeValues() {
        assertThatThrownBy(() -> productService.updateStock("prod-1", new UpdateStockRequest(-1)))
                .isInstanceOf(InvalidStockException.class);
    }

    @Test
    void updateStock_shouldUpdateQuantity() {
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        var response = productService.updateStock("prod-1", new UpdateStockRequest(50));

        assertThat(response.stockQuantity()).isEqualTo(50);
    }
}
