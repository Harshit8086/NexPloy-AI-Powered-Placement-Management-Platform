package com.nexploy.campuslink.auth.service;

import com.nexploy.campuslink.auth.entity.RefreshToken;
import com.nexploy.campuslink.auth.exception.InvalidRefreshTokenException;
import com.nexploy.campuslink.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${security.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMillis;

    /**
     * Issues a new refresh token for the user. Only the hash is persisted; the raw
     * token is returned to the caller and never stored or logged.
     */
    @Transactional
    public String issue(String username) {
        String rawToken = generateRawToken();
        OffsetDateTime now = OffsetDateTime.now();
        refreshTokenRepository.save(RefreshToken.builder()
            .id(UUID.randomUUID().toString())
            .tokenHash(hash(rawToken))
            .username(username)
            .issuedAt(now)
            .expiresAt(now.plusNanos(refreshExpirationMillis * 1_000_000))
            .revoked(false)
            .build());
        return rawToken;
    }

    /**
     * Validates a presented refresh token and rotates it: the presented token is
     * revoked and a fresh token is issued and returned. A revoked or expired token
     * is rejected.
     */
    @Transactional
    public Rotation rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is not recognized"));
        if (existing.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }
        if (existing.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }
        String newRawToken = issue(existing.getUsername());
        existing.setRevoked(true);
        existing.setReplacedBy(hash(newRawToken));
        refreshTokenRepository.save(existing);
        return new Rotation(existing.getUsername(), newRawToken);
    }

    /**
     * Revokes a refresh token if present. Unknown tokens are ignored so logout is idempotent.
     */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    /**
     * Revokes every active refresh token for a user ("sign out from all devices"). Returns the
     * number revoked. Note: access tokens are stateless JWTs and remain valid until they expire —
     * revoking refresh tokens stops session renewal on every device, which is the standard
     * server-side lever without an access-token blocklist.
     */
    @Transactional
    public int revokeAll(String username) {
        var tokens = refreshTokenRepository.findByUsernameAndRevokedFalse(username);
        tokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
        return tokens.size();
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record Rotation(String username, String rawToken) {
    }
}
