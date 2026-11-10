package com.financetracker.report;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cycles")
public class CycleReportController {

    private final CycleReportService reportService;

    public CycleReportController(CycleReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/current/report")
    public CycleReport current(@AuthenticationPrincipal Jwt jwt) {
        return reportService.currentReport(UUID.fromString(jwt.getSubject()));
    }

    // Report for the stored cycle containing the given date, e.g. the day before the current
    // cycle's start for the previous cycle.
    @GetMapping("/report")
    public CycleReport forDate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.reportForDate(UUID.fromString(jwt.getSubject()), date);
    }
}
