package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.entity.RefreshEntity;
import com.ceos24.cgv.domain.user.repository.RefreshRepository;
import com.ceos24.cgv.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshRepository refreshRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void addRefresh(String username, String refreshToken) {
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                jwtTokenProvider.getRefreshTokenExpiresAt(refreshToken), ZoneId.systemDefault());
        RefreshEntity refreshEntity = RefreshEntity.create(username, hashToken(refreshToken), expiresAt);

        refreshRepository.save(refreshEntity);
    }

    public boolean existsRefresh(String refreshToken) {
        return refreshRepository.existsByTokenHashAndExpiresAtAfter(hashToken(refreshToken), LocalDateTime.now());
    }

    @Transactional
    public void removeRefresh(String refreshToken) {
        refreshRepository.deleteByTokenHash(hashToken(refreshToken));
    }

    @Transactional
    public void removeRefreshUser(String username) {
        refreshRepository.deleteByUsername(username);
    }

    @Transactional
    public void removeExpiredTokens(LocalDateTime now) {
        refreshRepository.deleteByExpiresAtBefore(now);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
