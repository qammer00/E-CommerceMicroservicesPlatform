package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.document.Product;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String>, ProductRepositoryCustom {

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, String id);

    Optional<Product> findBySku(String sku);
}
