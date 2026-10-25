package com.financetracker.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.financetracker.user.User;
import com.financetracker.user.UserRepository;
import com.financetracker.web.ApiExceptionHandler.ApiError;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final boolean cookieSecure;
    // Used for unknown emails so login takes about as long whether or not the email exists.
    private final String dummyHash;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
            RefreshTokenService refreshTokenService, @Value("${app.cookie.secure}") boolean cookieSecure) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.cookieSecure = cookieSecure;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw emailTaken();
        }
        User user;
        try {
            // Flush now so a simultaneous duplicate registration fails here, not after the response.
            user = users.saveAndFlush(new User(request.email(), passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshTokenService.issue(user.getId())).toString())
                .body(new TokenResponse(tokenService.createAccessToken(user.getId())));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByEmail(request.email()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        // Same response for unknown email and wrong password.
        if (user == null || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshTokenService.issue(user.getId())).toString())
                .body(new TokenResponse(tokenService.createAccessToken(user.getId())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        Optional<RefreshTokenService.Rotated> rotated =
                refreshToken == null ? Optional.empty() : refreshTokenService.rotate(refreshToken);

        // Unknown, expired, revoked and missing tokens all get the same response, and the cookie is cleared.
        // Returned (not thrown) so the Set-Cookie header is included.
        if (rotated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                    .body(new ApiError("Unauthorized", "Invalid refresh token"));
        }

        UUID userId = rotated.get().userId();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(rotated.get().token()).toString())
                .body(new TokenResponse(tokenService.createAccessToken(userId)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .build();
    }

    private ResponseCookie refreshCookie(String token) {
        return cookieBuilder(token)
                .maxAge(Duration.ofDays(RefreshTokenService.LIFETIME_DAYS))
                .build();
    }

    private ResponseCookie clearedCookie() {
        return cookieBuilder("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder cookieBuilder(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth");
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Unable to register with these details");
    }
}
