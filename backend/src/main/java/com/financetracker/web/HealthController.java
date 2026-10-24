package com.financetracker.web;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Does not touch the database.
    @GetMapping(value = "/ping", produces = "text/plain")
    public String ping() {
        return "ok";
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean dbUp;
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbUp = true;
        } catch (Exception e) {
            dbUp = false;
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("db", dbUp ? "ok" : "down");
        body.put("time", OffsetDateTime.now().toString());

        HttpStatus status = dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }
}
