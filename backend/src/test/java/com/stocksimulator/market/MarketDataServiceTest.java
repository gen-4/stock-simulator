package com.stocksimulator.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stocksimulator.market.dto.StockQuote;
import com.stocksimulator.market.dto.StockSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private WebClient.RequestHeadersUriSpec getUriSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(webClient.get()).thenReturn(getUriSpec);
        lenient().when(getUriSpec.uri(anyString(), any(Object[].class))).thenReturn(getUriSpec);
        lenient().when(getUriSpec.uri(any(Function.class))).thenReturn(getUriSpec);
        lenient().when(getUriSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{}"));

        marketDataService = new MarketDataService(webClient, redisTemplate, objectMapper);
    }

    // ── getQuote tests ────────────────────────────────────────────────

    @Test
    void getQuote_cacheHit_returnsCachedQuote() throws Exception {
        StockQuote cached = StockQuote.builder()
                .symbol("AAPL").name("Apple Inc.").currentPrice(150.0)
                .change(2.5).changePercent(1.7).previousClose(147.5)
                .open(148.0).dayHigh(151.0).dayLow(147.0).volume(50000000L)
                .build();
        when(valueOperations.get("quote:AAPL")).thenReturn(objectMapper.writeValueAsString(cached));

        StockQuote result = marketDataService.getQuote("AAPL");

        assertNotNull(result);
        assertEquals("AAPL", result.getSymbol());
        assertEquals(150.0, result.getCurrentPrice());
        verify(webClient, never()).get();
    }

    @Test
    void getQuote_cacheMiss_returnsNullOnApiError() {
        when(valueOperations.get("quote:AAPL")).thenReturn(null);
        when(responseSpec.bodyToMono(String.class))
                .thenThrow(new org.springframework.web.reactive.function.client.WebClientResponseException(
                        404, "Not Found", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        StockQuote result = marketDataService.getQuote("AAPL");

        assertNull(result);
    }

    @Test
    void getQuote_invalidJson_returnsNull() {
        when(valueOperations.get("quote:AAPL")).thenReturn(null);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("not json at all"));

        StockQuote result = marketDataService.getQuote("AAPL");

        assertNull(result);
    }

    // ── getHistoricalPrices tests ─────────────────────────────────────

    @Test
    void getHistoricalPrices_cacheHit_returnsCachedPrices() throws Exception {
        String cached = "[{\"date\":\"2023-01-03\",\"open\":130.0,\"high\":132.0,\"low\":129.0,\"close\":131.0,\"adjustedClose\":131.0,\"volume\":1000000}]";
        when(valueOperations.get("history:AAPL:2023-01-03:2023-01-05")).thenReturn(cached);

        var result = marketDataService.getHistoricalPrices("AAPL",
                LocalDate.of(2023, 1, 3), LocalDate.of(2023, 1, 5));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(131.0, result.get(0).getClose());
    }

    @Test
    void getHistoricalPrices_apiError_returnsEmptyList() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(responseSpec.bodyToMono(String.class))
                .thenThrow(new org.springframework.web.reactive.function.client.WebClientResponseException(
                        500, "Server Error", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        var result = marketDataService.getHistoricalPrices("AAPL",
                LocalDate.of(2023, 1, 3), LocalDate.of(2023, 1, 5));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getHistoricalPrices_invalidJson_returnsEmptyList() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("garbage"));

        var result = marketDataService.getHistoricalPrices("AAPL",
                LocalDate.of(2023, 1, 3), LocalDate.of(2023, 1, 5));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── searchStocks tests ────────────────────────────────────────────

    @Test
    void searchStocks_validResponse_returnsResults() {
        String searchResponse = "{\"quotes\":[{\"symbol\":\"AAPL\",\"shortname\":\"Apple Inc.\",\"quoteType\":\"EQUITY\",\"exchange\":\"NMS\"}]}";
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(searchResponse));

        List<StockSearchResult> results = marketDataService.searchStocks("Apple");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("AAPL", results.get(0).getSymbol());
        assertEquals("Apple Inc.", results.get(0).getName());
    }

    @Test
    void searchStocks_emptyQuotes_returnsEmptyList() {
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"quotes\":[]}"));

        List<StockSearchResult> results = marketDataService.searchStocks("nonexistent");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchStocks_apiError_returnsEmptyList() {
        when(responseSpec.bodyToMono(String.class))
                .thenThrow(new org.springframework.web.reactive.function.client.WebClientResponseException(
                        500, "Server Error", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        List<StockSearchResult> results = marketDataService.searchStocks("Apple");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchStocks_invalidJson_returnsEmptyList() {
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("not json"));

        List<StockSearchResult> results = marketDataService.searchStocks("Apple");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ── Quote parsing tests (through public method) ───────────────────

    @Test
    void getQuote_missingChartNode_returnsNull() {
        when(valueOperations.get("quote:TEST")).thenReturn(null);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"chart\":{}}"));

        StockQuote result = marketDataService.getQuote("TEST");

        assertNull(result);
    }
}
