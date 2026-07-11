package com.stocksimulator.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stocksimulator.auth.dto.AuthResponse;
import com.stocksimulator.auth.dto.LoginRequest;
import com.stocksimulator.auth.dto.RefreshTokenRequest;
import com.stocksimulator.auth.dto.RegisterRequest;
import com.stocksimulator.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        AuthController authController = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AuthResponse mockAuthResponse() {
        return AuthResponse.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .tokenType("Bearer")
                .expiresIn(900000L)
                .build();
    }

    @Test
    void register_validRequest_returns201WithTokens() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(mockAuthResponse());

        RegisterRequest request = new RegisterRequest("newuser", "new@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("mock-refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void register_duplicateUsername_returns400() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Username is already taken"));

        RegisterRequest request = new RegisterRequest("existing", "a@b.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(mockAuthResponse());

        LoginRequest request = new LoginRequest("user1", "pass123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"));
    }

    @Test
    void login_invalidCredentials_returns400() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid username or password"));

        LoginRequest request = new LoginRequest("user1", "wrongpass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_validToken_returns200() throws Exception {
        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(mockAuthResponse());

        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"));
    }

    @Test
    void refresh_invalidToken_returns400() throws Exception {
        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid refresh token"));

        RefreshTokenRequest request = new RefreshTokenRequest("bad-token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_validToken_returns200WithMessage() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }
}
