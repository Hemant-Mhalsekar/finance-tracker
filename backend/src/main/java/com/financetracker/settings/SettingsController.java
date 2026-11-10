package com.financetracker.settings;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    public record PayDayRequest(
            @NotNull(message = "Pay day is required")
            @Min(value = 1, message = "Pay day must be between 1 and 31")
            @Max(value = 31, message = "Pay day must be between 1 and 31")
            Integer payDay) {
    }

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @PutMapping("/pay-day")
    public PayDaySettingsView changePayDay(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PayDayRequest request) {
        return settingsService.changePayDay(UUID.fromString(jwt.getSubject()), request.payDay());
    }
}
