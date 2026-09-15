package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.document.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public ProductRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Product> search(String category, Boolean active, String name, Pageable pageable) {
        List<Criteria> criteria = new ArrayList<>();

        if (StringUtils.hasText(category)) {
            criteria.add(Criteria.where("category").is(category));
        }
        if (active != null) {
            criteria.add(Criteria.where("active").is(active));
        }
        if (StringUtils.hasText(name)) {
            criteria.add(Criteria.where("name").regex(".*" + Pattern.quote(name) + ".*", "i"));
        }

        Query query = new Query();
        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));
        }
        query.with(pageable);

        List<Product> products = mongoTemplate.find(query, Product.class);
        return PageableExecutionUtils.getPage(
                products,
                pageable,
                () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Product.class));
    }
}
