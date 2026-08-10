package com.gameio.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gameio.common.error.UnauthorizedException;
import com.gameio.common.security.JwtProperties;
import com.gameio.user.UserRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    @Test
    void rejectsPasswordBeyondBcryptUtf8LimitBeforeHashComparison() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
        AuthService service = new AuthService(users, mock(RefreshTokenRepository.class), passwordEncoder,
                mock(RefreshTokenCodec.class), mock(JwtService.class), mock(JwtProperties.class), rateLimiter,
                Clock.systemUTC());
        LoginRequest request = new LoginRequest("player", "😀".repeat(20));

        assertThatThrownBy(() -> service.login(request, "client"))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("INVALID_CREDENTIALS"));

        verify(rateLimiter).check("client");
        verify(rateLimiter).recordFailure("client");
        verify(passwordEncoder, never()).matches(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(users);
    }
}
