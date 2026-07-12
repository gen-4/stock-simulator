package com.stocksimulator.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksimulator.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InflationServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InflationService inflationService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();

        lenient().when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        lenient().when(webClientBuilder.build()).thenReturn(webClient);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        inflationService = new InflationService(appProperties, webClientBuilder, redisTemplate, objectMapper);
    }

    // ── Existing tests ────────────────────────────────────────────────

    @Test
    void getCumulativeFactor_sameDate_returnsOne() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        double factor = inflationService.getCumulativeFactor(date, date);
        assertEquals(1.0, factor);
    }

    @Test
    void getCumulativeFactor_startAfterEnd_returnsOne() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2023, 1, 1);
        double factor = inflationService.getCumulativeFactor(start, end);
        assertEquals(1.0, factor);
    }

    @Test
    void getCumulativeFactor_nullDates_returnsOne() {
        assertEquals(1.0, inflationService.getCumulativeFactor(null, LocalDate.now()));
        assertEquals(1.0, inflationService.getCumulativeFactor(LocalDate.now(), null));
    }

    @Test
    void getCumulativeFactor_cacheHit_usesCache() throws Exception {
        Map<String, Double> cachedData = new HashMap<>();
        cachedData.put("2023-01-01", 300.0);
        cachedData.put("2023-06-01", 310.0);
        String json = objectMapper.writeValueAsString(cachedData);

        when(valueOperations.get("inflation:2023:2023")).thenReturn(json);

        double factor = inflationService.getCumulativeFactor(
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2023, 6, 1)
        );

        // 310/300 = 1.0333...
        assertTrue(factor > 1.03 && factor < 1.04);
        verify(valueOperations).get("inflation:2023:2023");
    }

    // ── New tests ─────────────────────────────────────────────────────

    @Test
    void constructor_constructsSuccessfully() {
        AppProperties appProperties = new AppProperties();
        assertDoesNotThrow(() ->
                new InflationService(appProperties, webClientBuilder, redisTemplate, objectMapper));
    }

    @Test
    void getCumulativeFactor_crossYear_usesCorrectCacheKey() {
        when(valueOperations.get("inflation:2022:2023")).thenReturn(null);

        when(webClient.post()).thenThrow(new RuntimeException("Connection refused"));

        double factor = inflationService.getCumulativeFactor(
                LocalDate.of(2022, 6, 1),
                LocalDate.of(2023, 6, 1)
        );

        assertEquals(1.0, factor);
        verify(valueOperations).get("inflation:2022:2023");
    }

    @Test
    void getCumulativeFactor_cacheReturnsInvalidJson_returnsOne() {
        when(valueOperations.get("inflation:2023:2023")).thenReturn("not-valid-json{{{");

        when(webClient.post()).thenThrow(new RuntimeException("Connection refused"));

        double factor = inflationService.getCumulativeFactor(
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2023, 6, 1)
        );

        assertEquals(1.0, factor);
    }

    @Test
    void getCumulativeFactor_cacheMiss_fetchesFromBLS() {
        when(valueOperations.get("inflation:2023:2023")).thenReturn(null);

        WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(reactor.core.publisher.Mono.just(
                        "{\"status\":\"REQUEST_SUCCEEDED\",\"Results\":{\"series\":[]}}"));

        inflationService.getCumulativeFactor(
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2023, 6, 1)
        );

        verify(webClient).post();
    }

    @Test
    void getCumulativeFactor_webClientThrows_returnsOne() {
        when(valueOperations.get("inflation:2023:2023")).thenReturn(null);

        when(webClient.post()).thenThrow(new RuntimeException("Connection refused"));

        double factor = inflationService.getCumulativeFactor(
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2023, 6, 1)
        );

        assertEquals(1.0, factor);
    }
}
