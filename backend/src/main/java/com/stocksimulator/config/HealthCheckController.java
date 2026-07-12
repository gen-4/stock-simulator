package com.stocksimulator.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthCheckController {

    private final DataSource dataSource;
    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());

        try (Connection conn = dataSource.getConnection()) {
            health.put("database", Map.of("status", "UP", "valid", conn.isValid(5)));
        } catch (Exception e) {
            log.error("Health check: database FAILED - {}", e.getMessage());
            health.put("database", Map.of("status", "DOWN", "error", e.getMessage()));
            health.put("status", "DOWN");
        }

        try {
            String pong = redisTemplate.execute((RedisConnection connection) -> connection.ping());
            health.put("redis", Map.of("status", "UP", "response", pong != null ? pong : "PONG"));
        } catch (Exception e) {
            log.error("Health check: redis FAILED - {}", e.getMessage());
            health.put("redis", Map.of("status", "DOWN", "error", e.getMessage()));
            health.put("status", "DOWN");
        }

        ResponseEntity<Map<String, Object>> response = "DOWN".equals(health.get("status"))
                ? ResponseEntity.status(503).body(health)
                : ResponseEntity.ok(health);

        log.debug("Health check completed: status={}", health.get("status"));
        return response;
    }
}
