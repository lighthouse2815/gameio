package com.gameio.auth;

import com.gameio.common.error.UnauthorizedException;
import com.gameio.common.security.RefreshCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshCookieProperties cookieProperties;

    public AuthController(AuthService authService, RefreshCookieProperties cookieProperties) {
        this.authService = authService;
        this.cookieProperties = cookieProperties;
        if ("None".equalsIgnoreCase(cookieProperties.sameSite()) && !cookieProperties.secure()) {
            throw new IllegalStateException("SameSite=None refresh cookies must also be Secure");
        }
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResult result = authService.register(request);
        return withRefreshCookie(result, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthResult result = authService.login(request, servletRequest.getRemoteAddr());
        return withRefreshCookie(result, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        AuthResult result = authService.refresh(readRefreshCookie(request));
        return withRefreshCookie(result, HttpStatus.OK);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        String refreshToken = readOptionalRefreshCookie(request);
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResult result, HttpStatus status) {
        Duration maxAge = Duration.between(Instant.now(), result.refreshExpiresAt());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), maxAge).toString())
                .body(result.response());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieProperties.name(), value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/api/auth")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge);
        if (cookieProperties.domain() != null && !cookieProperties.domain().isBlank()) {
            builder.domain(cookieProperties.domain());
        }
        return builder.build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        String value = readOptionalRefreshCookie(request);
        if (value == null) {
            throw new UnauthorizedException("REFRESH_TOKEN_MISSING", "Refresh token cookie is missing");
        }
        return value;
    }

    private String readOptionalRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieProperties.name().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
