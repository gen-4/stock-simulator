package com.stocksimulator.auth;

import com.stocksimulator.auth.dto.AuthResponse;
import com.stocksimulator.auth.dto.LoginRequest;
import com.stocksimulator.auth.dto.RefreshTokenRequest;
import com.stocksimulator.auth.dto.RegisterRequest;
import com.stocksimulator.user.User;
import com.stocksimulator.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.expiration}")
    private long accessExpirationMs;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for username='{}', email='{}'", request.getUsername(), request.getEmail());

        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Registration failed: username '{}' already taken", request.getUsername());
            throw new IllegalArgumentException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: email '{}' already in use", request.getEmail());
            throw new IllegalArgumentException("Email is already in use");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        log.info("User registered successfully: username='{}', id={}", user.getUsername(), user.getId());
        return generateAndStoreTokens(user.getUsername(), user.getId());
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for username='{}'", request.getUsername());

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                    request.getUsername(),
                    request.getPassword()
            )
        );

        if (!authentication.isAuthenticated()) {
            log.warn("Login failed for username='{}': not authenticated", request.getUsername());
            throw new IllegalArgumentException("Invalid username or password");
        }

        // Look up the user to get the ID for token embedding
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        log.info("Login successful for username='{}'", request.getUsername());
        return generateAndStoreTokens(user.getUsername(), user.getId());
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refresh token request received");

        String token = request.getRefreshToken();
        String username = jwtTokenProvider.getUsernameFromToken(token);
        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        if (username == null) {
            log.warn("Refresh failed: could not extract username from token");
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String redisKey = REFRESH_TOKEN_KEY_PREFIX + username;
        String storedToken = redisTemplate.opsForValue().get(redisKey);

        if (storedToken == null || !storedToken.equals(token)) {
            log.warn("Refresh failed for user '{}': token mismatch or expired", username);
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        // Rotate: delete old token and issue new pair
        redisTemplate.delete(redisKey);
        AuthResponse generatedTokens = generateAndStoreTokens(username, userId);
        log.info("Refresh token rotated for user '{}'", username);

        return generatedTokens;
    }

    public void logout(String refreshToken) {
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        if (username != null) {
            String redisKey = REFRESH_TOKEN_KEY_PREFIX + username;
            redisTemplate.delete(redisKey);
            log.info("Logged out user '{}': refresh token deleted from Redis", username);
        } else {
            log.warn("Logout called with invalid refresh token");
        }
    }

    private AuthResponse generateAndStoreTokens(String username, Long userId) {
        String accessToken = jwtTokenProvider.generateAccessToken(username, userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(username, userId);

        String redisKey = REFRESH_TOKEN_KEY_PREFIX + username;
        redisTemplate.opsForValue().set(redisKey, refreshToken, refreshExpirationMs, TimeUnit.MILLISECONDS);

        log.debug("Generated token pair for user '{}' (id={}), stored refresh token in Redis with {}ms TTL",
                username, userId, refreshExpirationMs);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessExpirationMs)
                .build();
    }
}
