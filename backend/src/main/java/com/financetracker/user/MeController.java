package com.financetracker.user;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MeController {

    // pendingPayDay and pendingPayDayFrom are null unless a pay day change is scheduled.
    public record MeResponse(UUID id, String email, Short payDay, Short pendingPayDay, LocalDate pendingPayDayFrom) {
    }

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        User user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        return new MeResponse(user.getId(), user.getEmail(), user.getPayDay(),
                user.getPendingPayDay(), user.getPendingPayDayFrom());
    }
}
