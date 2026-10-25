package com.financetracker.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.financetracker.user.User;
import com.financetracker.user.UserRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    // Used for unknown emails so login takes about as long whether or not the email exists.
    private final String dummyHash;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw emailTaken();
        }
        User user = users.save(new User(request.email(), passwordEncoder.encode(request.password())));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TokenResponse(tokenService.createAccessToken(user.getId())));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByEmail(request.email()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        // Same response for unknown email and wrong password.
        if (user == null || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return new TokenResponse(tokenService.createAccessToken(user.getId()));
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Unable to register with these details");
    }
}
