package com.stocksimulator.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksimulator.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class InflationService {

    private static final String CACHE_PREFIX = "inflation:";

    private final WebClient webClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public InflationService(
            AppProperties appProperties,
            WebClient.Builder webClientBuilder,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.appProperties = appProperties;
        this.webClient = webClientBuilder
                .baseUrl(appProperties.getBls().getBaseUrl())
                .build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the cumulative inflation factor between two dates.
     *
     * <p>The factor is computed as {@code CPI(endDate) / CPI(startDate)}.
     * A value of 1.05 means that prices rose by 5% between the two dates.</p>
     *
     * <p>Results are cached in Redis for the configured TTL. If the BLS API call fails
     * or data is unavailable, a fallback factor of 1.0 (no inflation) is returned.</p>
     */
    public double getCumulativeFactor(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            log.warn("Null date provided for inflation factor calculation, returning 1.0");
            return 1.0;
        }

        if (!endDate.isAfter(startDate)) {
            return 1.0;
        }

        int startYear = startDate.getYear();
        int endYear = endDate.getYear();

        String cacheKey = CACHE_PREFIX + startYear + ":" + endYear;

        // ── Try cache first ─────────────────────────────────────────────
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for inflation factor: {}", cacheKey);
                Map<String, Double> cachedData = objectMapper.readValue(
                        cached,
                        objectMapper.getTypeFactory().constructMapLikeType(
                                HashMap.class, String.class, Double.class));
                return computeFactorFromCache(cachedData, startDate, endDate);
            }
        } catch (Exception e) {
            log.warn("Failed to read inflation cache for key {}: {}", cacheKey, e.getMessage());
        }

        // ── Fetch from BLS API ──────────────────────────────────────────
        log.info("Fetching CPI data from BLS for year range {}–{}", startYear, endYear);

        try {
            Map<LocalDate, Double> cpiData = fetchCpiData(startYear, endYear);

            if (cpiData.isEmpty()) {
                log.warn("No CPI data returned for year range {}–{}, falling back to 1.0", startYear, endYear);
                return 1.0;
            }

            cacheCpiData(cacheKey, cpiData);

            return computeFactor(cpiData, startDate, endDate);

        } catch (Exception e) {
            log.error("Failed to fetch CPI data from BLS: {}. Falling back to 1.0", e.getMessage(), e);
            return 1.0;
        }
    }

    /**
     * Fetches monthly CPI data from the BLS public API.
     */
    public Map<LocalDate, Double> fetchCpiData(int startYear, int endYear) {
        Map<LocalDate, Double> cpiData = new HashMap<>();

        try {
            String cpiSeriesId = appProperties.getBls().getCpiSeriesId();
            String requestBody = objectMapper.writeValueAsString(Map.of(
                "seriesid", new String[]{cpiSeriesId},
                "startyear", String.valueOf(startYear),
                "endyear", String.valueOf(endYear)
            ));

            String responseBody = webClient.post()
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (responseBody == null) {
                log.warn("Empty response body from BLS API");
                return cpiData;
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String status = root.path("status").asText("");

            if (!"REQUEST_SUCCEEDED".equals(status)) {
                log.error("BLS API returned non-success status: {}", status);
                String message = root.path("message").toString();
                log.error("BLS API message: {}", message);
                return cpiData;
            }

            JsonNode results = root.path("Results").path("series");
            if (results.isMissingNode() || !results.isArray() || results.isEmpty()) {
                log.warn("No series data in BLS response");
                return cpiData;
            }

            JsonNode seriesData = results.get(0).path("data");
            if (seriesData.isMissingNode() || !seriesData.isArray()) {
                log.warn("No data array in BLS series response");
                return cpiData;
            }

            for (JsonNode dataPoint : seriesData) {
                String year = dataPoint.path("year").asText(null);
                String period = dataPoint.path("period").asText(null);
                String value = dataPoint.path("value").asText(null);

                if (year == null || period == null || value == null) {
                    continue;
                }

                if (!period.startsWith("M")) {
                    continue;
                }

                try {
                    int monthNum = Integer.parseInt(period.substring(1));
                    double cpiValue = Double.parseDouble(value);

                    LocalDate monthDate = LocalDate.of(Integer.parseInt(year), monthNum, 1);
                    cpiData.put(monthDate, cpiValue);
                } catch (NumberFormatException e) {
                    log.debug("Skipping BLS data point with year={}, period={}, value={}", year, period, value);
                }
            }

            log.info("Parsed {} monthly CPI data points from BLS for year range {}–{}", cpiData.size(), startYear, endYear);

        } catch (WebClientResponseException e) {
            log.error("BLS API HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error fetching CPI data from BLS: {}", e.getMessage(), e);
        }

        return cpiData;
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private Double findClosestCpi(Map<LocalDate, Double> cpiData, LocalDate targetDate) {
        YearMonth targetMonth = YearMonth.from(targetDate);

        for (int i = 0; i <= 2; i++) {
            YearMonth candidate = targetMonth.minusMonths(i);
            LocalDate candidateDate = candidate.atDay(1);
            Double cpi = cpiData.get(candidateDate);
            if (cpi != null) {
                return cpi;
            }
        }
        return null;
    }

    private double computeFactor(Map<LocalDate, Double> cpiData, LocalDate startDate, LocalDate endDate) {
        Double startCpi = findClosestCpi(cpiData, startDate);
        Double endCpi = findClosestCpi(cpiData, endDate);

        if (startCpi == null || endCpi == null) {
            log.warn("Could not find CPI values for start={} (found={}) or end={} (found={})",
                startDate, startCpi, endDate, endCpi
            );
            return 1.0;
        }

        if (startCpi == 0) {
            log.warn("Start CPI is zero for date {}, returning 1.0", startDate);
            return 1.0;
        }

        double factor = endCpi / startCpi;
        log.debug("Inflation factor from {} to {}: {} / {} = {}",
            startDate, endDate, endCpi, startCpi, factor
        );
        return factor;
    }

    private double computeFactorFromCache(Map<String, Double> cachedData, LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<LocalDate, Double> dateMap = new HashMap<>();
        for (Map.Entry<String, Double> entry : cachedData.entrySet()) {
            dateMap.put(LocalDate.parse(entry.getKey(), fmt), entry.getValue());
        }
        return computeFactor(dateMap, startDate, endDate);
    }

    private void cacheCpiData(String cacheKey, Map<LocalDate, Double> cpiData) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            Map<String, Double> serializable = new HashMap<>();
            for (Map.Entry<LocalDate, Double> entry : cpiData.entrySet()) {
                serializable.put(entry.getKey().format(fmt), entry.getValue());
            }
            String json = objectMapper.writeValueAsString(serializable);
            redisTemplate.opsForValue().set(cacheKey, json,
                    appProperties.getCache().getInflationTtlHours(), TimeUnit.HOURS);
            log.debug("Cached CPI data with key={} and {} entries", cacheKey, cpiData.size());
        } catch (Exception e) {
            log.warn("Failed to cache CPI data: {}", e.getMessage());
        }
    }
}
