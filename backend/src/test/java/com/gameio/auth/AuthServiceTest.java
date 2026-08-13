package com.gameio.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gameio.common.error.ServiceUnavailableException;
import com.gameio.common.error.UnauthorizedException;
import com.gameio.common.security.JwtProperties;
import com.gameio.user.UserAccount;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class AuthServiceTest {
    @Test
    void rejectsPasswordBeyondBcryptUtf8LimitBeforeHashComparison() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
        AuthService service = new AuthService(users, mock(RefreshTokenRepository.class), passwordEncoder,
                mock(RefreshTokenCodec.class), mock(JwtService.class), mock(JwtProperties.class), rateLimiter,
                mock(GoogleIdTokenVerifier.class), mock(UserIdentityRepository.class),
                mock(PlatformTransactionManager.class), Clock.systemUTC());
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

    @Test
    void googleIdentityOutageDoesNotCountAsAnInvalidLoginAttempt() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
        GoogleIdTokenVerifier googleVerifier = mock(GoogleIdTokenVerifier.class);
        org.mockito.Mockito.when(googleVerifier.verify("credential"))
                .thenThrow(new ServiceUnavailableException(
                        "GOOGLE_IDENTITY_UNAVAILABLE", "Google identity verification is temporarily unavailable"));
        AuthService service = new AuthService(users, mock(RefreshTokenRepository.class), passwordEncoder,
                mock(RefreshTokenCodec.class), mock(JwtService.class), mock(JwtProperties.class), rateLimiter,
                googleVerifier, mock(UserIdentityRepository.class), mock(PlatformTransactionManager.class),
                Clock.systemUTC());

        assertThatThrownBy(() -> service.loginWithGoogle(new GoogleLoginRequest("credential"), "client"))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(rateLimiter).check("client");
        verify(rateLimiter, never()).recordFailure("client");
    }

    @Test
    void retriesGoogleProvisioningInANewTransactionAfterAConcurrentInsertWins() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        UserRepository users = mock(UserRepository.class);
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        RefreshTokenCodec refreshTokenCodec = mock(RefreshTokenCodec.class);
        JwtService jwtService = mock(JwtService.class);
        LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
        GoogleIdTokenVerifier googleVerifier = mock(GoogleIdTokenVerifier.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus firstTransaction = mock(TransactionStatus.class);
        TransactionStatus retryTransaction = mock(TransactionStatus.class);

        UserAccount winner = UserAccount.createProviderOnly("winner", "winner@gmail.com", now);
        UserIdentity winnerIdentity = UserIdentity.create(
                winner, IdentityProvider.GOOGLE, "stable-subject", now);
        when(googleVerifier.verify("credential"))
                .thenReturn(new VerifiedGoogleIdentity("stable-subject", "winner@gmail.com"));
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(firstTransaction, retryTransaction);
        when(identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "stable-subject"))
                .thenReturn(Optional.empty(), Optional.of(winnerIdentity));
        when(users.existsByEmailNormalized("winner@gmail.com")).thenReturn(false);
        when(users.existsByUsernameNormalized("winner")).thenReturn(false);
        when(users.saveAndFlush(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("concurrent winner committed"));
        when(refreshTokenCodec.generate()).thenReturn("refresh-token");
        when(refreshTokenCodec.hash("refresh-token")).thenReturn("refresh-hash");
        when(jwtService.issue(winner)).thenReturn(new JwtService.AccessToken(
                "access-token", now.plusSeconds(900)));

        AuthService service = new AuthService(users, refreshTokens, mock(PasswordEncoder.class), refreshTokenCodec,
                jwtService, new JwtProperties("gameio-api", "secret", Duration.ofMinutes(15), Duration.ofDays(30)),
                rateLimiter, googleVerifier, identities, transactionManager, clock);

        AuthResult result = service.loginWithGoogle(new GoogleLoginRequest("credential"), "client");

        assertThat(result.response().user().id()).isEqualTo(winner.getId());
        verify(transactionManager).rollback(firstTransaction);
        verify(transactionManager).commit(retryTransaction);
        verify(rateLimiter).reset("client");
    }
}
