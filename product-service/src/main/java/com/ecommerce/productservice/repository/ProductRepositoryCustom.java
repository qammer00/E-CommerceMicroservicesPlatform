package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.document.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepositoryCustom {

    Page<Product> search(String category, Boolean active, String name, Pageable pageable);
}
