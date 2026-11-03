package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.entity.RefreshEntity;
import com.ceos24.cgv.domain.user.repository.RefreshRepository;
import com.ceos24.cgv.global.exception.JwtTokenExpiredException;
import com.ceos24.cgv.global.exception.JwtTokenInvalidException;
import com.ceos24.cgv.global.jwt.JwtProperties;
import com.ceos24.cgv.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshRepository repository;
    private JwtTokenProvider provider;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshRepository.class);
        provider = provider(Duration.ofDays(14));
        service = new RefreshTokenService(repository, provider);
    }

    @Test
    void 토큰원문대신_해시와_JWT의_만료시각을_저장하고_같은해시로_조회와_삭제한다() throws Exception {
        String token = provider.createRefreshToken("buyer", "ROLE_USER");
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));

        service.addRefresh("buyer", token);

        var saved = ArgumentCaptor.forClass(RefreshEntity.class);
        verify(repository).save(saved.capture());
        assertEquals("buyer", saved.getValue().getUsername());
        assertEquals(expectedHash, saved.getValue().getTokenHash());
        assertNotEquals(token, saved.getValue().getTokenHash());
        assertEquals(LocalDateTime.ofInstant(provider.getRefreshTokenExpiresAt(token), ZoneId.systemDefault()),
                saved.getValue().getExpiresAt());

        when(repository.existsByTokenHashAndExpiresAtAfter(eq(expectedHash), any())).thenReturn(true);
        assertTrue(service.existsRefresh(token));
        verify(repository).existsByTokenHashAndExpiresAtAfter(eq(expectedHash), any(LocalDateTime.class));

        service.removeRefresh(token);
        verify(repository).deleteByTokenHash(expectedHash);
    }

    @Test
    void 액세스토큰은_리프레시토큰으로_저장하지_않는다() {
        String access = provider.createAccessToken("buyer", "ROLE_USER");

        assertThrows(JwtTokenInvalidException.class, () -> service.addRefresh("buyer", access));
        verifyNoInteractions(repository);
    }

    @Test
    void 만료된_리프레시토큰은_저장하지_않는다() {
        String expired = provider(Duration.ofSeconds(-1)).createRefreshToken("buyer", "ROLE_USER");

        assertThrows(JwtTokenExpiredException.class, () -> service.addRefresh("buyer", expired));
        verifyNoInteractions(repository);
    }

    private JwtTokenProvider provider(Duration refreshExpiration) {
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        return new JwtTokenProvider(new JwtProperties(secret, Duration.ofHours(1), refreshExpiration, "cgv-test"));
    }
}
