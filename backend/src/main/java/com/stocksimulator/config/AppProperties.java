package com.stocksimulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cache cache = new Cache();
    private RateLimit rateLimit = new RateLimit();
    private YahooFinance yahooFinance = new YahooFinance();
    private Bls bls = new Bls();

    @Data
    public static class Cache {
        private long quoteTtlSeconds = 60;
        private long historyTtlSeconds = 3600;
        private long inflationTtlHours = 24;
    }

    @Data
    public static class RateLimit {
        private int maxRequestsPerMinute = 10;
        private long windowMs = 60_000;
    }

    @Data
    public static class YahooFinance {
        private String baseUrl = "https://query1.finance.yahoo.com";
        private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        private int maxInMemorySize = 16 * 1024 * 1024;
        private int searchQuotesCount = 10;
    }

    @Data
    public static class Bls {
        private String baseUrl = "https://api.bls.gov/publicAPI/v2/timeseries/data/";
        private String cpiSeriesId = "CUSR0000SA0";
    }
}
