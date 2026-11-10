package com.financetracker.cycle;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/cycles")
public class CycleController {

    public record SalaryRequest(
            @NotNull(message = "Salary is required")
            @Min(value = 100, message = "Salary must be between ₹1 and ₹1,00,00,000")
            @Max(value = 1_000_000_000L, message = "Salary must be between ₹1 and ₹1,00,00,000")
            Long salaryPaise) {
    }

    private final CycleService cycleService;

    public CycleController(CycleService cycleService) {
        this.cycleService = cycleService;
    }

    // A GET that can write: it creates any missing cycles up to today. Safe to repeat.
    @GetMapping("/current")
    public CycleView current(@AuthenticationPrincipal Jwt jwt) {
        return cycleService.getCurrent(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/current/salary")
    public CycleView changeSalary(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SalaryRequest request) {
        return cycleService.changeCurrentSalary(UUID.fromString(jwt.getSubject()), request.salaryPaise());
    }
}
