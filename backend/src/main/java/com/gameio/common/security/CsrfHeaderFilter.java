package com.gameio.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

final class CsrfHeaderFilter extends OncePerRequestFilter {
    static final String HEADER_NAME = "X-Gameio-CSRF";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"1".equals(request.getHeader(HEADER_NAME))) {
            SecurityErrorWriter.write(response, 403, "CSRF_HEADER_REQUIRED",
                    "The X-Gameio-CSRF header is required", request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
