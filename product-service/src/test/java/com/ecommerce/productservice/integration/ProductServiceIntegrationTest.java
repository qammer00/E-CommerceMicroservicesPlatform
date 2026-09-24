package com.ecommerce.productservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.productservice.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductServiceIntegrationTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0").withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> withDatabase(mongo.getConnectionString(), "ecommerce_catalog_test"));
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void cleanDatabase() {
        productRepository.deleteAll();
        var productsCache = cacheManager.getCache("products");
        var listsCache = cacheManager.getCache("productLists");
        if (productsCache != null) {
            productsCache.clear();
        }
        if (listsCache != null) {
            listsCache.clear();
        }
    }

    @Test
    void createGetListUpdateAndCacheProduct() throws Exception {
        String createBody = """
                {
                  "name": "Mechanical Keyboard",
                  "description": "RGB keyboard",
                  "sku": "KB-100",
                  "price": 89.99,
                  "stockQuantity": 25,
                  "category": "Electronics",
                  "imageUrl": "https://cdn.example.com/kb.png",
                  "active": true
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sku").value("KB-100"))
                .andReturn();

        String productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        assertThat(productId).isNotBlank();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Mechanical Keyboard"));

        assertThat(cacheManager.getCache("products")).isNotNull();
        assertThat(cacheManager.getCache("products").get(productId)).isNotNull();

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc")
                        .param("category", "Electronics")
                        .param("active", "true")
                        .param("name", "Keyboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(put("/api/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pro Mechanical Keyboard","price":99.99}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Pro Mechanical Keyboard"));

        mockMvc.perform(patch("/api/products/" + productId + "/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockQuantity":40}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(40));

        mockMvc.perform(patch("/api/products/" + productId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(delete("/api/products/" + productId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProduct_shouldValidateRequiredFields() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "sku": "",
                                  "price": -1,
                                  "stockQuantity": -5,
                                  "category": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
