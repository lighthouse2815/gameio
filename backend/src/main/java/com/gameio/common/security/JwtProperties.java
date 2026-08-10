package com.gameio.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gameio.security.jwt")
public record JwtProperties(String issuer, String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
