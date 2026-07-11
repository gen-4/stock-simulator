package com.stocksimulator.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class InflationService {

    private static final Logger log = LoggerFactory.getLogger(InflationService.class);

    private static final String BLS_BASE_URL = "https://api.bls.gov/publicAPI/v2/timeseries/data/";
    private static final String CPI_SERIES_ID = "CUSR0000SA0";
    private static final String CACHE_PREFIX = "inflation:";
    private static final long CACHE_TTL_HOURS = 24;

    private final WebClient webClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public InflationService(
            WebClient.Builder webClientBuilder,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder
                .baseUrl(BLS_BASE_URL)
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
     * <p>Results are cached in Redis for 24 hours. If the BLS API call fails
     * or data is unavailable, a fallback factor of 1.0 (no inflation) is returned.</p>
     *
     * @param startDate the earlier date
     * @param endDate   the later date
     * @return the cumulative inflation multiplier (>= 1.0 for positive inflation)
     */
    public double getCumulativeFactor(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            log.warn("Null date provided for inflation factor calculation, returning 1.0");
            return 1.0;
        }

        if (!endDate.isAfter(startDate)) {
            // Same date or start after end — no inflation adjustment needed
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

            // Cache the raw data for future lookups in this year range
            cacheCpiData(cacheKey, cpiData);

            return computeFactor(cpiData, startDate, endDate);

        } catch (Exception e) {
            log.error("Failed to fetch CPI data from BLS: {}. Falling back to 1.0", e.getMessage(), e);
            return 1.0;
        }
    }

    /**
     * Fetches monthly CPI data from the BLS public API.
     *
     * <p>BLS limits free-tier requests to 10 years of data per call. If the
     * requested range exceeds that, only the first 10 years will be returned.</p>
     *
     * <p>The API accepts a POST request with a JSON body containing the series ID
     * and the year range. The response includes annual and monthly data; only
     * monthly data points are extracted.</p>
     *
     * @param startYear the earliest year to fetch
     * @param endYear   the latest year to fetch
     * @return map of month-start dates to their CPI values
     */
    public Map<LocalDate, Double> fetchCpiData(int startYear, int endYear) {
        Map<LocalDate, Double> cpiData = new HashMap<>();

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "seriesid", new String[]{CPI_SERIES_ID},
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

                // Only process monthly data (M01–M12)
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

    /**
     * Finds the CPI value closest to (but not after) the given target date.
     * If the exact month is not available, looks backwards for up to 2 months.
     */
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

    /**
     * Computes the inflation factor from a CPI data map for the given date range.
     */
    private double computeFactor(Map<LocalDate, Double> cpiData, LocalDate startDate, LocalDate endDate) {
        Double startCpi = findClosestCpi(cpiData, startDate);
        Double endCpi = findClosestCpi(cpiData, endDate);

        if (startCpi == null || endCpi == null) {
            log.warn("Could not find CPI values for start={} (found={}) or end={} (found={})",
                    startDate, startCpi, endDate, endCpi);
            return 1.0;
        }

        if (startCpi == 0) {
            log.warn("Start CPI is zero for date {}, returning 1.0", startDate);
            return 1.0;
        }

        double factor = endCpi / startCpi;
        log.debug("Inflation factor from {} to {}: {} / {} = {}",
                startDate, endDate, endCpi, startCpi, factor);
        return factor;
    }

    /**
     * Computes the inflation factor from a cached data map (stored as String->Double keys).
     */
    private double computeFactorFromCache(Map<String, Double> cachedData, LocalDate startDate, LocalDate endDate) {
        // Convert String keys back to LocalDate for comparison
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<LocalDate, Double> dateMap = new HashMap<>();
        for (Map.Entry<String, Double> entry : cachedData.entrySet()) {
            dateMap.put(LocalDate.parse(entry.getKey(), fmt), entry.getValue());
        }
        return computeFactor(dateMap, startDate, endDate);
    }

    /**
     * Serialises CPI data to JSON and caches it in Redis.
     */
    private void cacheCpiData(String cacheKey, Map<LocalDate, Double> cpiData) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            Map<String, Double> serializable = new HashMap<>();
            for (Map.Entry<LocalDate, Double> entry : cpiData.entrySet()) {
                serializable.put(entry.getKey().format(fmt), entry.getValue());
            }
            String json = objectMapper.writeValueAsString(serializable);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.debug("Cached CPI data with key={} and {} entries", cacheKey, cpiData.size());
        } catch (Exception e) {
            log.warn("Failed to cache CPI data: {}", e.getMessage());
        }
    }
}
