package com.gameio.common.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gameio.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
