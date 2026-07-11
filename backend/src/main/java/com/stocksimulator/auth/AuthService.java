package com.stocksimulator.auth;

import com.stocksimulator.auth.dto.AuthResponse;
import com.stocksimulator.auth.dto.LoginRequest;
import com.stocksimulator.auth.dto.RefreshTokenRequest;
import com.stocksimulator.auth.dto.RegisterRequest;
import com.stocksimulator.user.User;
import com.stocksimulator.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";
    private static final long REFRESH_TOKEN_TTL_DAYS = 7;
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 900_000L; // 15 minutes

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       RedisTemplate<String, String> redisTemplate,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.authenticationManager = authenticationManager;
    }

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
        return generateAndStoreTokens(user.getUsername());
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

        log.info("Login successful for username='{}'", request.getUsername());
        return generateAndStoreTokens(request.getUsername());
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refresh token request received");

        String token = request.getRefreshToken();
        String username = jwtTokenProvider.getUsernameFromToken(token);

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
        log.info("Refresh token rotated for user '{}'", username);

        return generateAndStoreTokens(username);
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

    private AuthResponse generateAndStoreTokens(String username) {
        String accessToken = jwtTokenProvider.generateAccessToken(username);
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        String redisKey = REFRESH_TOKEN_KEY_PREFIX + username;
        redisTemplate.opsForValue().set(redisKey, refreshToken, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);

        log.debug("Generated token pair for user '{}', stored refresh token in Redis with {} day TTL",
                username, REFRESH_TOKEN_TTL_DAYS);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(ACCESS_TOKEN_EXPIRATION_MS)
                .build();
    }
}
