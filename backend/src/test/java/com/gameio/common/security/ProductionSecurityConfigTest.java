package com.gameio.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.observability.ObservabilityProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityConfigTest {
    private final ProductionSecurityConfig config = new ProductionSecurityConfig();

    @Test
    void failsFastWhenProductionSecretIsMissingOrUsesLocalFallback() {
        JwtProperties fallback = properties(ProductionSecurityConfig.LOCAL_FALLBACK);
        SmartInitializingSingleton missing = config.productionJwtSecretGuard(new MockEnvironment(), fallback);
        assertThatThrownBy(missing::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");

        MockEnvironment fallbackEnvironment = new MockEnvironment()
                .withProperty("JWT_SECRET", ProductionSecurityConfig.LOCAL_FALLBACK);
        SmartInitializingSingleton reused = config.productionJwtSecretGuard(fallbackEnvironment, fallback);
        assertThatThrownBy(reused::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsExplicitNonDefaultProductionSecret() {
        String secret = "production-secret-with-more-than-thirty-two-random-characters";
        MockEnvironment environment = new MockEnvironment().withProperty("JWT_SECRET", secret);

        assertThatCode(config.productionJwtSecretGuard(environment, properties(secret))::afterSingletonsInstantiated)
                .doesNotThrowAnyException();
    }

    @Test
    void requiresASeparateStrongProductionMetricsToken() {
        ObservabilityProperties fallback = new ObservabilityProperties(
                ProductionSecurityConfig.LOCAL_METRICS_FALLBACK);
        SmartInitializingSingleton missing = config.productionMetricsTokenGuard(new MockEnvironment(), fallback);
        assertThatThrownBy(missing::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("METRICS_TOKEN");

        String token = "production-metrics-token-with-at-least-thirty-two-characters";
        MockEnvironment environment = new MockEnvironment().withProperty("METRICS_TOKEN", token);
        assertThatCode(config.productionMetricsTokenGuard(
                environment, new ObservabilityProperties(token))::afterSingletonsInstantiated)
                .doesNotThrowAnyException();
    }

    private JwtProperties properties(String secret) {
        return new JwtProperties("gameio-api", secret, Duration.ofMinutes(15), Duration.ofDays(30));
    }
}
