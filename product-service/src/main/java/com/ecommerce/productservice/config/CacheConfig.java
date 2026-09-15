package com.ecommerce.productservice.config;

import com.ecommerce.productservice.service.ProductService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
@EnableMongoAuditing
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${app.cache.products-ttl-ms:300000}") long productsTtlMs,
            @Value("${app.cache.product-lists-ttl-ms:120000}") long productListsTtlMs) {

        JdkSerializationRedisSerializer valueSerializer = new JdkSerializationRedisSerializer();

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults.entryTtl(Duration.ofMillis(productsTtlMs)))
                .withCacheConfiguration(
                        ProductService.CACHE_PRODUCTS,
                        defaults.entryTtl(Duration.ofMillis(productsTtlMs)))
                .withCacheConfiguration(
                        ProductService.CACHE_PRODUCT_LISTS,
                        defaults.entryTtl(Duration.ofMillis(productListsTtlMs)))
                .build();
    }
}
