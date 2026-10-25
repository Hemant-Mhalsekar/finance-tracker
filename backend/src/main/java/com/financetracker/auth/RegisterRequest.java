package com.financetracker.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotNull(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password) {

    // Normalize first so validation and storage both see the trimmed, lowercase email.
    public RegisterRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }

    // BCrypt ignores anything beyond 72 bytes, so reject longer passwords.
    @AssertTrue(message = "Password must be at most 72 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
