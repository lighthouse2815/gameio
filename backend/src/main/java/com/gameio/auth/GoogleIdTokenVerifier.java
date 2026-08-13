package com.gameio.auth;

interface GoogleIdTokenVerifier {
    VerifiedGoogleIdentity verify(String credential);
}
