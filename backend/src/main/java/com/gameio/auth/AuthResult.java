package com.gameio.auth;

import java.time.Instant;

record AuthResult(AuthResponse response, String refreshToken, Instant refreshExpiresAt) {
}
