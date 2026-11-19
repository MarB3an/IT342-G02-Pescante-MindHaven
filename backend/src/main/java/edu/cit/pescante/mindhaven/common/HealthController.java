package edu.cit.pescante.mindhaven.common;

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
@RequestMapping("/api/v1/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "mindhaven-backend");
        body.put("timestamp", Instant.now().toString());
        try (Connection conn = dataSource.getConnection()) {
            body.put("database", conn.isValid(3) ? "UP" : "DOWN");
            body.put("databaseProduct", conn.getMetaData().getDatabaseProductName());
        } catch (Exception ex) {
            body.put("status", "DEGRADED");
            body.put("database", "DOWN");
        }
        return ResponseEntity.ok(body);
    }
}
