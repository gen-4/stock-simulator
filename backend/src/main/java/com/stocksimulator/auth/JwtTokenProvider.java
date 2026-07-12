package com.stocksimulator.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "userId";

    private final SecretKey secretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration}") long refreshExpirationMs) {
        if (secret == null || secret.isBlank() || secret.equals("myDefaultSecretKey12345678901234567890")) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable must be set to a secure random string. " +
                "The default value is not safe for production use."
            );
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * Generate an access token for the given username and userId.
     */
    public String generateAccessToken(String username, Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationMs);

        String token = Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();

        log.info("Generated access token for user '{}' (id={}) expires at {}", username, userId, expiry);
        return token;
    }

    /**
     * Generate a refresh token for the given username and userId.
     */
    public String generateRefreshToken(String username, Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);

        String token = Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();

        log.info("Generated refresh token for user '{}' (id={}) expires at {}", username, userId, expiry);
        return token;
    }

    /**
     * Extract the username (subject) from a token.
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    /**
     * Extract the userId from a token.
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    /**
     * Extract the expiration date from a token.
     */
    public Date getExpirationFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration();
    }

    /**
     * Validate a JWT token. Returns true if the token is well-formed, signed,
     * and not expired; false otherwise.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    // ------------------------------------------------------------------ //
    //  Internal helpers                                                    //
    // ------------------------------------------------------------------ //

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
