package com.stocksimulator.auth;

import com.stocksimulator.auth.dto.AuthResponse;
import com.stocksimulator.auth.dto.LoginRequest;
import com.stocksimulator.auth.dto.RefreshTokenRequest;
import com.stocksimulator.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register - username='{}', email='{}'", request.getUsername(), request.getEmail());
        AuthResponse response = authService.register(request);
        log.info("Registration successful for user '{}'", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login - username='{}'", request.getUsername());
        AuthResponse response = authService.login(request);
        log.info("Login successful for user '{}'", request.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("POST /api/auth/refresh");
        AuthResponse response = authService.refreshToken(request);
        log.info("Token refresh successful");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("POST /api/auth/logout");
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Collections.singletonMap("message", "Logged out successfully"));
    }
}
