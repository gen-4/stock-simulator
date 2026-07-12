package com.stocksimulator.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksimulator.config.AppProperties;
import com.stocksimulator.market.dto.HistoricalPrice;
import com.stocksimulator.market.dto.StockQuote;
import com.stocksimulator.market.dto.StockSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private static final String QUOTE_CACHE_PREFIX = "quote:";
    private static final String HISTORY_CACHE_PREFIX = "history:";

    @Qualifier("yahooFinanceWebClient")
    private final WebClient webClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    /**
     * Fetches a real-time stock quote for the given symbol.
     * Results are cached in Redis for the configured TTL.
     */
    public StockQuote getQuote(String symbol) {
        String cacheKey = QUOTE_CACHE_PREFIX + symbol.toUpperCase();

        // Check cache first
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for quote: {}", symbol);
                return objectMapper.readValue(cached, StockQuote.class);
            }
        } catch (Exception e) {
            log.warn("Failed to read quote from cache for symbol {}: {}", symbol, e.getMessage());
        }

        log.info("Fetching quote from Yahoo Finance for symbol: {}", symbol);

        try {
            String response = webClient.get()
                    .uri("/v8/finance/chart/{symbol}?interval=1d&range=1d", symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            StockQuote quote = parseQuoteResponse(response, symbol);

            // Cache the result
            if (quote != null) {
                try {
                    String json = objectMapper.writeValueAsString(quote);
                    redisTemplate.opsForValue().set(cacheKey, json,
                            appProperties.getCache().getQuoteTtlSeconds(), TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Failed to cache quote for symbol {}: {}", symbol, e.getMessage());
                }
            }

            return quote;

        } catch (WebClientResponseException e) {
            log.error("Yahoo Finance API error for quote {}: HTTP {} - {}", symbol, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch quote for symbol {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetches historical daily prices for the given symbol and date range.
     * Results are cached in Redis for the configured TTL.
     */
    public List<HistoricalPrice> getHistoricalPrices(String symbol, LocalDate startDate, LocalDate endDate) {
        String cacheKey = HISTORY_CACHE_PREFIX + symbol.toUpperCase() + ":" + startDate + ":" + endDate;

        // Check cache first
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for historical prices: {} ({})", symbol, startDate);
                return objectMapper.readValue(
                    cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, HistoricalPrice.class)
                );
            }
        } catch (Exception e) {
            log.warn("Failed to read historical prices from cache for symbol {}: {}", symbol, e.getMessage());
        }

        log.info("Fetching historical prices from Yahoo Finance for symbol: {} from {} to {}", symbol, startDate, endDate);

        long period1 = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().getEpochSecond();
        long period2 = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().getEpochSecond();

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("period1", period1)
                        .queryParam("period2", period2)
                        .queryParam("interval", "1d")
                        .build(symbol))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<HistoricalPrice> prices = parseHistoricalResponse(response);

            // Cache the result
            if (!prices.isEmpty()) {
                try {
                    String json = objectMapper.writeValueAsString(prices);
                    redisTemplate.opsForValue().set(cacheKey, json,
                            appProperties.getCache().getHistoryTtlSeconds(), TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Failed to cache historical prices for symbol {}: {}", symbol, e.getMessage());
                }
            }

            return prices;

        } catch (WebClientResponseException e) {
            log.error("Yahoo Finance API error for historical prices {}: HTTP {} - {}", symbol, e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch historical prices for symbol {}: {}", symbol, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Searches for stocks matching the given query string.
     */
    public List<StockSearchResult> searchStocks(String query) {
        log.info("Searching stocks with query: {}", query);

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/finance/search")
                            .queryParam("q", query)
                            .queryParam("quotesCount", appProperties.getYahooFinance().getSearchQuotesCount())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseSearchResponse(response);

        } catch (WebClientResponseException e) {
            log.error("Yahoo Finance API error for stock search '{}': HTTP {} - {}", query, e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to search stocks with query '{}': {}", query, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Response parsing helpers
    // -------------------------------------------------------------------------

    private StockQuote parseQuoteResponse(String responseBody, String symbol) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode chart = root.path("chart").path("result").path(0);

            if (chart.isMissingNode()) {
                log.warn("No chart data found in response for symbol: {}", symbol);
                return null;
            }

            JsonNode meta = chart.path("meta");
            JsonNode indicators = chart.path("indicators").path("quote").path(0);

            double currentPrice = meta.path("regularMarketPrice").asDouble(0.0);
            double previousClose = meta.path("chartPreviousClose").asDouble(0.0);
            double change = currentPrice - previousClose;
            double changePercent = previousClose != 0 ? (change / previousClose) * 100.0 : 0.0;

            JsonNode timestamps = chart.path("timestamp");
            double open = 0.0;
            double dayHigh = 0.0;
            double dayLow = 0.0;
            long volume = 0;

            if (!timestamps.isMissingNode() && timestamps.isArray() && timestamps.size() > 0) {
                JsonNode opens = indicators.path("open");
                JsonNode highs = indicators.path("high");
                JsonNode lows = indicators.path("low");
                JsonNode volumes = indicators.path("volume");

                int lastIndex = timestamps.size() - 1;
                open = opens.path(0).asDouble(0.0);
                dayHigh = highs.path(lastIndex).asDouble(0.0);
                dayLow = lows.path(lastIndex).asDouble(0.0);
                volume = volumes.path(lastIndex).asLong(0);
            }

            String shortName = meta.path("shortName").asText(symbol);

            return StockQuote.builder()
                    .symbol(symbol.toUpperCase())
                    .name(shortName)
                    .currentPrice(currentPrice)
                    .change(change)
                    .changePercent(changePercent)
                    .previousClose(previousClose)
                    .open(open)
                    .dayHigh(dayHigh)
                    .dayLow(dayLow)
                    .volume(volume)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse quote response for symbol {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    private List<HistoricalPrice> parseHistoricalResponse(String responseBody) {
        List<HistoricalPrice> prices = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode chart = root.path("chart").path("result").path(0);

            if (chart.isMissingNode()) {
                log.warn("No chart data found in historical response");
                return prices;
            }

            JsonNode timestamps = chart.path("timestamp");
            JsonNode quoteIndicators = chart.path("indicators").path("quote").path(0);
            JsonNode adjCloseIndicators = chart.path("indicators").path("adjclose").path(0);

            if (timestamps.isMissingNode() || !timestamps.isArray()) {
                return prices;
            }

            JsonNode opens = quoteIndicators.path("open");
            JsonNode highs = quoteIndicators.path("high");
            JsonNode lows = quoteIndicators.path("low");
            JsonNode closes = quoteIndicators.path("close");
            JsonNode volumes = quoteIndicators.path("volume");
            JsonNode adjustedCloses = adjCloseIndicators.path("adjclose");

            for (int i = 0; i < timestamps.size(); i++) {
                long epochSeconds = timestamps.get(i).asLong();
                LocalDate date = Instant.ofEpochSecond(epochSeconds)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                double open = opens.path(i).asDouble(0.0);
                double high = highs.path(i).asDouble(0.0);
                double low = lows.path(i).asDouble(0.0);
                double close = closes.path(i).asDouble(0.0);
                long volume = volumes.path(i).asLong(0);

                Double adjustedClose = null;
                if (!adjustedCloses.isMissingNode() && adjustedCloses.isArray() && i < adjustedCloses.size()) {
                    adjustedClose = adjustedCloses.get(i).isNull() ? null : adjustedCloses.get(i).asDouble();
                }

                prices.add(HistoricalPrice.builder()
                        .date(date)
                        .open(open)
                        .high(high)
                        .low(low)
                        .close(close)
                        .adjustedClose(adjustedClose != null ? adjustedClose : close)
                        .volume(volume)
                        .build());
            }

        } catch (Exception e) {
            log.error("Failed to parse historical price response: {}", e.getMessage(), e);
        }

        return prices;
    }

    private List<StockSearchResult> parseSearchResponse(String responseBody) {
        List<StockSearchResult> results = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode quotes = root.path("quotes");

            if (quotes.isMissingNode() || !quotes.isArray()) {
                log.warn("No quotes found in search response");
                return results;
            }

            for (JsonNode quote : quotes) {
                String symbol = quote.path("symbol").asText(null);
                String name = quote.path("shortname").asText(quote.path("longname").asText(null));
                String type = quote.path("quoteType").asText(null);
                String exchange = quote.path("exchange").asText(null);

                if (symbol != null) {
                    results.add(StockSearchResult.builder()
                            .symbol(symbol)
                            .name(name)
                            .type(type)
                            .exchange(exchange)
                            .build());
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse search response: {}", e.getMessage(), e);
        }

        return results;
    }
}
