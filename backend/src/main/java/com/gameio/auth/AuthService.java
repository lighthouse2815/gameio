package com.gameio.auth;

import com.gameio.common.error.ConflictException;
import com.gameio.common.error.UnauthorizedException;
import com.gameio.common.security.JwtProperties;
import com.gameio.user.UserAccount;
import com.gameio.user.UserRepository;
import com.gameio.user.UserResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenCodec refreshTokenCodec;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final UserIdentityRepository userIdentities;
    private final TransactionTemplate googleLoginTransactions;
    private final Clock clock;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            RefreshTokenCodec refreshTokenCodec,
            JwtService jwtService,
            JwtProperties jwtProperties,
            LoginRateLimiter loginRateLimiter,
            GoogleIdTokenVerifier googleIdTokenVerifier,
            UserIdentityRepository userIdentities,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenCodec = refreshTokenCodec;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.loginRateLimiter = loginRateLimiter;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.userIdentities = userIdentities;
        this.googleLoginTransactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        validatePasswordBytes(request.password());
        String normalizedUsername = UserAccount.normalize(request.username());
        String normalizedEmail = UserAccount.normalize(request.email());
        if (users.existsByUsernameNormalized(normalizedUsername)) {
            throw new ConflictException("USERNAME_TAKEN", "Username is already in use");
        }
        if (users.existsByEmailNormalized(normalizedEmail)) {
            throw new ConflictException("EMAIL_TAKEN", "Email is already in use");
        }
        UserAccount user = UserAccount.create(request.username(), request.email(),
                passwordEncoder.encode(request.password()), Instant.now(clock));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("ACCOUNT_ALREADY_EXISTS", "Username or email is already in use");
        }
        return issueTokenPair(user, UUID.randomUUID());
    }

    @Transactional
    public AuthResult login(LoginRequest request, String clientKey) {
        loginRateLimiter.check(clientKey);
        if (passwordExceedsBcryptLimit(request.password())) {
            loginRateLimiter.recordFailure(clientKey);
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Login or password is invalid");
        }
        String normalized = UserAccount.normalize(request.login());
        UserAccount user = users.findByUsernameNormalized(normalized)
                .or(() -> users.findByEmailNormalized(normalized))
                .orElse(null);
        if (user == null || !user.hasPassword()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(clientKey);
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Login or password is invalid");
        }
        loginRateLimiter.reset(clientKey);
        return issueTokenPair(user, UUID.randomUUID());
    }

    public AuthResult loginWithGoogle(GoogleLoginRequest request, String clientKey) {
        loginRateLimiter.check(clientKey);
        VerifiedGoogleIdentity google;
        try {
            google = googleIdTokenVerifier.verify(request.idToken());
        } catch (UnauthorizedException exception) {
            loginRateLimiter.recordFailure(clientKey);
            throw exception;
        }

        AuthResult result;
        try {
            result = completeGoogleLogin(google);
        } catch (DataIntegrityViolationException firstConflict) {
            try {
                result = completeGoogleLogin(google);
            } catch (DataIntegrityViolationException repeatedConflict) {
                throw googleAccountConflict();
            }
        }
        loginRateLimiter.reset(clientKey);
        return result;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String rawRefreshToken) {
        String presentedHash = refreshTokenCodec.hash(rawRefreshToken);
        RefreshToken current = refreshTokens.findForUpdateByTokenHash(presentedHash)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token is invalid"));
        if (current.isRevoked()) {
            refreshTokens.revokeActiveFamily(current.getFamilyId(), Instant.now(clock));
            throw new UnauthorizedException("REFRESH_TOKEN_REUSED", "Refresh token reuse was detected");
        }
        Instant now = Instant.now(clock);
        if (current.isExpired(now)) {
            refreshTokens.revokeActiveFamily(current.getFamilyId(), now);
            throw new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }

        String rawReplacement = refreshTokenCodec.generate();
        String replacementHash = refreshTokenCodec.hash(rawReplacement);
        current.rotate(replacementHash, now);
        RefreshToken replacement = RefreshToken.create(current.getUser(), current.getFamilyId(), replacementHash,
                now.plus(jwtProperties.refreshTokenTtl()), now);
        refreshTokens.save(replacement);
        return response(current.getUser(), rawReplacement, replacement.getExpiresAt());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = refreshTokenCodec.hash(rawRefreshToken);
        refreshTokens.findForUpdateByTokenHash(hash)
                .ifPresent(token -> refreshTokens.revokeActiveFamily(token.getFamilyId(), Instant.now(clock)));
    }

    private AuthResult issueTokenPair(UserAccount user, UUID familyId) {
        Instant now = Instant.now(clock);
        String rawRefreshToken = refreshTokenCodec.generate();
        Instant refreshExpiresAt = now.plus(jwtProperties.refreshTokenTtl());
        refreshTokens.save(RefreshToken.create(user, familyId, refreshTokenCodec.hash(rawRefreshToken),
                refreshExpiresAt, now));
        return response(user, rawRefreshToken, refreshExpiresAt);
    }

    private AuthResult completeGoogleLogin(VerifiedGoogleIdentity google) {
        return Objects.requireNonNull(googleLoginTransactions.execute(status -> {
            UserAccount user = userIdentities
                    .findByProviderAndProviderSubject(IdentityProvider.GOOGLE, google.subject())
                    .map(UserIdentity::getUser)
                    .orElseGet(() -> createGoogleUser(google));
            return issueTokenPair(user, UUID.randomUUID());
        }));
    }

    private UserAccount createGoogleUser(VerifiedGoogleIdentity google) {
        String normalizedEmail = UserAccount.normalize(google.email());
        if (users.existsByEmailNormalized(normalizedEmail)) {
            throw new ConflictException("GOOGLE_ACCOUNT_LINK_REQUIRED",
                    "Sign in with the existing account before linking this Google identity");
        }

        UserAccount user = UserAccount.createProviderOnly(
                uniqueGoogleUsername(google), google.email(), Instant.now(clock));
        users.saveAndFlush(user);
        userIdentities.saveAndFlush(UserIdentity.create(
                user, IdentityProvider.GOOGLE, google.subject(), Instant.now(clock)));
        return user;
    }

    private String uniqueGoogleUsername(VerifiedGoogleIdentity google) {
        String emailLocalPart = google.email().substring(0, google.email().indexOf('@'));
        String stem = emailLocalPart.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (stem.length() < 3) {
            stem = "player";
        }
        String direct = stem.substring(0, Math.min(stem.length(), 24));
        if (!users.existsByUsernameNormalized(UserAccount.normalize(direct))) {
            return direct;
        }

        String fingerprint = subjectFingerprint(google.subject());
        for (int suffixLength = 6; suffixLength <= 20; suffixLength += 2) {
            String suffix = "_" + fingerprint.substring(0, suffixLength);
            int stemLength = Math.min(stem.length(), 24 - suffix.length());
            String candidate = stem.substring(0, stemLength) + suffix;
            if (!users.existsByUsernameNormalized(UserAccount.normalize(candidate))) {
                return candidate;
            }
        }
        throw new ConflictException("GOOGLE_USERNAME_UNAVAILABLE",
                "A unique username could not be generated for this Google account");
    }

    private String subjectFingerprint(String subject) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ConflictException googleAccountConflict() {
        return new ConflictException("GOOGLE_ACCOUNT_CONFLICT", "Google account could not be linked safely");
    }

    private AuthResult response(UserAccount user, String refreshToken, Instant refreshExpiresAt) {
        JwtService.AccessToken accessToken = jwtService.issue(user);
        AuthResponse response = new AuthResponse("Bearer", accessToken.value(), accessToken.expiresAt(),
                UserResponse.from(user));
        return new AuthResult(response, refreshToken, refreshExpiresAt);
    }

    private void validatePasswordBytes(String password) {
        if (passwordExceedsBcryptLimit(password)) {
            throw new ConflictException("PASSWORD_TOO_LONG", "Password must not exceed 72 UTF-8 bytes");
        }
    }

    private boolean passwordExceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72;
    }
}
