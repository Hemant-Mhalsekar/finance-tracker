package com.financetracker.onboarding;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.financetracker.cycle.CycleView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
public class OnboardingController {

    public record OnboardingRequest(
            @Min(value = 100, message = "Salary must be between ₹1 and ₹1,00,00,000")
            @Max(value = 1_000_000_000L, message = "Salary must be between ₹1 and ₹1,00,00,000")
            long salaryPaise,

            @Min(value = 1, message = "Pay day must be between 1 and 31")
            @Max(value = 31, message = "Pay day must be between 1 and 31")
            int payDay) {
    }

    public record OnboardingResponse(int payDay, CycleView cycle) {
    }

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/api/onboarding")
    public ResponseEntity<OnboardingResponse> onboard(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OnboardingRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CycleView cycle = onboardingService.onboard(userId, request.salaryPaise(), request.payDay());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OnboardingResponse(request.payDay(), cycle));
    }
}

