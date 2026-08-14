package com.gameio.common.security;

import com.gameio.observability.ObservabilityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MetricsTokenFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Gameio-Metrics-Token";
    private static final String ENDPOINT = "/actuator/prometheus";

    private final ObservabilityProperties properties;

    public MetricsTokenFilter(ObservabilityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals(request.getContextPath() + ENDPOINT);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String expected = properties.metricsToken();
        String supplied = request.getHeader(HEADER_NAME);
        if (expected == null || expected.isBlank() || supplied == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            SecurityErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_METRICS_TOKEN", "A valid metrics token is required", request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
