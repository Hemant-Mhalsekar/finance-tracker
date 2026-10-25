package com.financetracker.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    public static final long LIFETIME_DAYS = 30;

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository repository;

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /** Result of a successful rotation: the owner and the new raw token (for the cookie). */
    public record Rotated(UUID userId, String token) {
    }

    /** Creates and stores a new refresh token. Returns the raw token; only its hash is stored. */
    @Transactional
    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(new RefreshToken(userId, hash(rawToken), OffsetDateTime.now().plusDays(LIFETIME_DAYS)));
        return rawToken;
    }

    /**
     * Revokes the presented token and issues a new one. Returns empty if the token is unknown,
     * expired or revoked. It returns instead of throwing so the transaction commits and the
     * "revoke all" on reuse detection is not rolled back.
     * Ownership of the token is decided only by the conditional UPDATE in revokeIfActive,
     * so two simultaneous requests with the same token cannot both succeed.
     */
    @Transactional
    public Optional<Rotated> rotate(String rawToken) {
        String hash = hash(rawToken);

        if (repository.revokeIfActive(hash, OffsetDateTime.now()) == 1) {
            UUID userId = repository.findByTokenHash(hash).orElseThrow().getUserId();
            return Optional.of(new Rotated(userId, issue(userId)));
        }

        // Not active: unknown, expired, or already revoked. Only the last case is reuse.
        Optional<RefreshToken> found = repository.findByTokenHash(hash);
        if (found.isPresent() && found.get().isRevoked()) {
            // A revoked token was used again: assume it was stolen and end all of this user's sessions.
            UUID userId = found.get().getUserId();
            repository.revokeAllByUserId(userId);
            log.warn("Refresh token reuse detected for user {}; all refresh tokens revoked", userId);
        }
        return Optional.empty();
    }

    /** Revokes the token if it exists and is still valid. Does nothing otherwise. */
    @Transactional
    public void revoke(String rawToken) {
        repository.revokeIfActive(hash(rawToken), OffsetDateTime.now());
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required on every Java platform.
            throw new IllegalStateException(e);
        }
    }
}
