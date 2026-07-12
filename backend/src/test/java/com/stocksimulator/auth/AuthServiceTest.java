package com.stocksimulator.auth;

import com.stocksimulator.auth.dto.AuthResponse;
import com.stocksimulator.auth.dto.LoginRequest;
import com.stocksimulator.auth.dto.RefreshTokenRequest;
import com.stocksimulator.auth.dto.RegisterRequest;
import com.stocksimulator.user.User;
import com.stocksimulator.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Inject @Value properties
        ReflectionTestUtils.setField(authService, "accessExpirationMs", 900_000L);
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604_800_000L);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(jwtTokenProvider.generateAccessToken(anyString(), any())).thenReturn("mock-access-token");
        lenient().when(jwtTokenProvider.generateRefreshToken(anyString(), any())).thenReturn("mock-refresh-token");
    }

    @Test
    void register_newUser_returnsTokens() {
        RegisterRequest request = new RegisterRequest("newuser", "new@example.com", "password123");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900_000L, response.getExpiresIn());
        verify(userRepository).save(any(User.class));
        verify(valueOperations).set(eq("refresh_token:newuser"), eq("mock-refresh-token"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void register_duplicateUsername_throwsException() {
        RegisterRequest request = new RegisterRequest("existing", "a@b.com", "password123");
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));
        assertEquals("Username is already taken", ex.getMessage());
    }

    @Test
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = new RegisterRequest("newuser", "taken@example.com", "password123");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));
        assertEquals("Email is already in use", ex.getMessage());
    }

    @Test
    void login_validCredentials_returnsTokens() {
        LoginRequest request = new LoginRequest("user1", "pass123");
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        // Set up user lookup for login
        User mockUser = User.builder().id(1L).username("user1").build();
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(mockUser));

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        verify(userRepository).findByUsername("user1");
    }

    @Test
    void refreshToken_validToken_rotatesAndReturns() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        when(jwtTokenProvider.getUsernameFromToken("valid-refresh-token")).thenReturn("user1");
        when(jwtTokenProvider.getUserIdFromToken("valid-refresh-token")).thenReturn(1L);
        when(valueOperations.get("refresh_token:user1")).thenReturn("valid-refresh-token");

        AuthResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        verify(redisTemplate).delete("refresh_token:user1");
        verify(valueOperations).set(eq("refresh_token:user1"), eq("mock-refresh-token"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void refreshToken_invalidToken_throwsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("bad-token");
        when(jwtTokenProvider.getUsernameFromToken("bad-token")).thenReturn("user1");
        when(jwtTokenProvider.getUserIdFromToken("bad-token")).thenReturn(1L);
        when(valueOperations.get("refresh_token:user1")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> authService.refreshToken(request));
    }

    @Test
    void logout_deletesRefreshToken() {
        when(jwtTokenProvider.getUsernameFromToken("refresh-token")).thenReturn("user1");

        authService.logout("refresh-token");

        verify(redisTemplate).delete("refresh_token:user1");
    }
}
