package com.ecommerce.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(String currency, String provider) {
}
