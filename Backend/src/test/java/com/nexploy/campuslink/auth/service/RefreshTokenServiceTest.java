package com.nexploy.campuslink.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.RefreshToken;
import com.nexploy.campuslink.auth.exception.InvalidRefreshTokenException;
import com.nexploy.campuslink.auth.repository.RefreshTokenRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository);
        ReflectionTestUtils.setField(service, "refreshExpirationMillis", 604_800_000L);
    }

    @Test
    void issuePersistsHashNotRawToken() {
        String rawToken = service.issue("officer");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        org.mockito.Mockito.verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(rawToken).isNotBlank();
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getUsername()).isEqualTo("officer");
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void rotateRevokesPresentedTokenAndReturnsFreshToken() {
        RefreshToken existing = valid("officer");
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        RefreshTokenService.Rotation rotation = service.rotate("presented-raw-token");

        assertThat(rotation.username()).isEqualTo("officer");
        assertThat(rotation.rawToken()).isNotBlank().isNotEqualTo("presented-raw-token");
        assertThat(existing.isRevoked()).isTrue();
        assertThat(existing.getReplacedBy()).isNotBlank();
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope"))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateRejectsRevokedToken() {
        RefreshToken revoked = valid("officer");
        revoked.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate("presented"))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshToken expired = valid("officer");
        expired.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("presented"))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private RefreshToken valid(String username) {
        OffsetDateTime now = OffsetDateTime.now();
        return RefreshToken.builder()
            .id("rt")
            .tokenHash("stored-hash")
            .username(username)
            .issuedAt(now)
            .expiresAt(now.plusDays(7))
            .revoked(false)
            .build();
    }
}
