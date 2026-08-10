package com.gameio.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gameio.security.refresh-cookie")
public record RefreshCookieProperties(String name, boolean secure, String sameSite, String domain) {
}
