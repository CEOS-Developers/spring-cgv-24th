package com.ceos.cgv.domain.auth.service;

import com.ceos.cgv.domain.auth.dto.LoginResponse;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.domain.auth.security.JwtService;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse issueForLogin(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return rotate(user);
    }

    @Transactional
    public LoginResponse reissue(String refreshToken) {
        User user = currentHolder(refreshToken);
        return rotate(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        currentHolder(refreshToken).revokeRefreshToken();
    }

    private User currentHolder(String refreshToken) {
        Long userId;
        try {
            userId = jwtService.verifyRefresh(refreshToken);
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (user.getRefreshTokenHash() == null || !MessageDigest.isEqual(
                user.getRefreshTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(refreshToken).getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return user;
    }

    private LoginResponse rotate(User user) {
        String refreshToken = jwtService.issueRefresh(user.getId());
        user.replaceRefreshTokenHash(hash(refreshToken));
        return LoginResponse.bearer(jwtService.issue(user.getId(), user.getRole()), refreshToken,
                jwtService.expiresInSeconds(), jwtService.refreshExpiresInSeconds());
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
