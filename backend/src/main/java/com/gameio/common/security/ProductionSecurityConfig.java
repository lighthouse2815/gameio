package com.gameio.common.security;

import com.gameio.observability.ObservabilityProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionSecurityConfig {
    static final String LOCAL_FALLBACK = "local-development-secret-change-before-deploying-2026";
    static final String LOCAL_METRICS_FALLBACK = "local-development-metrics-token-change-before-deploying";

    @Bean
    SmartInitializingSingleton productionJwtSecretGuard(Environment environment, JwtProperties properties) {
        return () -> {
            String configuredSecret = environment.getProperty("JWT_SECRET");
            if (configuredSecret == null || configuredSecret.isBlank()
                    || LOCAL_FALLBACK.equals(configuredSecret)
                    || LOCAL_FALLBACK.equals(properties.secret())) {
                throw new IllegalStateException(
                        "Production requires an explicit JWT_SECRET different from the local development secret");
            }
        };
    }

    @Bean
    SmartInitializingSingleton productionMetricsTokenGuard(
            Environment environment,
            ObservabilityProperties properties) {
        return () -> {
            String configuredToken = environment.getProperty("METRICS_TOKEN");
            if (configuredToken == null || configuredToken.isBlank()
                    || configuredToken.getBytes(StandardCharsets.UTF_8).length < 32
                    || LOCAL_METRICS_FALLBACK.equals(configuredToken)
                    || LOCAL_METRICS_FALLBACK.equals(properties.metricsToken())) {
                throw new IllegalStateException(
                        "Production requires an explicit METRICS_TOKEN with at least 32 UTF-8 bytes");
            }
        };
    }
}
