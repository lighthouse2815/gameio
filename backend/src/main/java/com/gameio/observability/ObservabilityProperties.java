package com.gameio.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gameio.observability")
public record ObservabilityProperties(String metricsToken) {
}
