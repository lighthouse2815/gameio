package com.gameio.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gameio.security.google")
public record GoogleIdentityProperties(String clientId, String jwkSetUri) {
    public boolean configured() {
        return clientId != null && !clientId.isBlank();
    }
}
