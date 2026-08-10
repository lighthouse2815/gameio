package com.gameio.common.security;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionSecurityConfig {
    static final String LOCAL_FALLBACK = "local-development-secret-change-before-deploying-2026";

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
}
