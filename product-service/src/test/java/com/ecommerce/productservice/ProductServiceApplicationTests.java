package com.ecommerce.productservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProductServiceApplicationTests {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0").withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> withDatabase(mongo.getConnectionString(), "ecommerce_catalog"));
    }

    private static String withDatabase(String connectionString, String database) {
        int queryIndex = connectionString.indexOf('?');
        if (queryIndex >= 0) {
            String base = connectionString.substring(0, queryIndex);
            String query = connectionString.substring(queryIndex);
            return (base.endsWith("/") ? base : base + "/") + database + query;
        }
        return (connectionString.endsWith("/") ? connectionString : connectionString + "/") + database;
    }

    @Test
    void contextLoads() {
    }
}
