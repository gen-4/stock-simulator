package com.stocksimulator.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private static final String TEST_SECRET = "testSecretKey12345678901234567890123456";
    private static final long ACCESS_EXPIRATION = 900000L; // 15 min
    private static final long REFRESH_EXPIRATION = 604800000L; // 7 days

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
    }

    @Test
    void generateAccessToken_returnsNonEmptyToken() {
        String token = jwtTokenProvider.generateAccessToken("testuser");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateRefreshToken_returnsNonEmptyToken() {
        String token = jwtTokenProvider.generateRefreshToken("testuser");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void getUsernameFromToken_returnsCorrectUsername() {
        String token = jwtTokenProvider.generateAccessToken("alice");
        String username = jwtTokenProvider.getUsernameFromToken(token);
        assertEquals("alice", username);
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = jwtTokenProvider.generateAccessToken("testuser");
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_returnsFalseForInvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("totally.invalid.token"));
    }

    @Test
    void validateToken_returnsFalseForEmptyToken() {
        assertFalse(jwtTokenProvider.validateToken(""));
    }

    @Test
    void getExpirationFromToken_returnsDateInFuture() {
        String token = jwtTokenProvider.generateAccessToken("testuser");
        var expiration = jwtTokenProvider.getExpirationFromToken(token);
        assertNotNull(expiration);
        assertTrue(expiration.after(new java.util.Date()));
    }

    @Test
    void accessAndRefreshTokensHaveDifferentExpirations() {
        String access = jwtTokenProvider.generateAccessToken("testuser");
        String refresh = jwtTokenProvider.generateRefreshToken("testuser");

        var accessExp = jwtTokenProvider.getExpirationFromToken(access);
        var refreshExp = jwtTokenProvider.getExpirationFromToken(refresh);

        assertTrue(refreshExp.after(accessExp),
                "Refresh token should expire after access token");
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() throws InterruptedException {
        // Create a provider with 1ms expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(TEST_SECRET, 1L, 1L);
        String token = shortLivedProvider.generateAccessToken("testuser");

        // Wait for token to expire
        Thread.sleep(50);

        assertFalse(shortLivedProvider.validateToken(token),
                "Expired token should be invalid");
    }

    @Test
    void validateToken_returnsFalseForTokenSignedWithDifferentSecret() {
        String otherSecret = "otherSecretKey123456789012345678901234";
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherSecret, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
        String tokenFromOther = otherProvider.generateAccessToken("testuser");

        assertFalse(jwtTokenProvider.validateToken(tokenFromOther),
                "Token signed with different secret should be invalid");
    }
}
