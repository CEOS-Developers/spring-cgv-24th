package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.TokenReissueResponse;
import com.ceos24.cgv.domain.user.entity.RefreshToken;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.RefreshTokenRepository;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.global.security.RefreshTokenProvider;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

// 리프레시 토큰의 DB 상태를 바꾸는 트랜잭션 경계. AuthService가 이 경계 바깥에서 잠금 대기 초과를 응답으로 바꾼다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenProvider refreshTokenProvider;
    private final JwtProvider jwtProvider;
    private final Clock clock;

    // 원문은 응답으로 한 번만 내보내고 DB에는 해시만 남긴다. 로그인마다 새 묶음이라 기기별 토큰이 따로 산다.
    @Transactional
    public String issue(Long userId) {
        String rawToken = refreshTokenProvider.generate();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(userRepository.getReferenceById(userId))
                .tokenHash(refreshTokenProvider.hash(rawToken))
                .familyId(UUID.randomUUID().toString())
                .expiresAt(refreshTokenProvider.expiresAt(LocalDateTime.now(clock)))
                .build());
        return rawToken;
    }

    // 사용 완료 표시와 새 토큰 저장이 한 트랜잭션이다. 어느 쪽이 실패해도 둘 다 되돌아가서
    // "사용 완료인데 다음 토큰이 없는" 상태가 생기지 않고, 클라이언트는 같은 토큰으로 다시 시도할 수 있다.
    // 역할은 DB의 사용자에서 읽는다. 권한이 바뀌었다면 재발급 시점에 반영된다.
    @Transactional
    public TokenReissueResponse reissue(String rawRefreshToken) {
        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(refreshTokenProvider.hash(rawRefreshToken))
                .orElseThrow(() -> rejected("not_found", null));

        LocalDateTime now = LocalDateTime.now(clock);
        if (!current.isUsableAt(now)) {
            throw rejected(rejectReason(current), current.getId());
        }

        String nextRawToken = refreshTokenProvider.generate();
        RefreshToken next = refreshTokenRepository.save(current.rotate(refreshTokenProvider.hash(nextRawToken), now));

        User user = current.getUser();
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        return TokenReissueResponse.of(accessToken, jwtProvider.getAccessTokenValiditySeconds(),
                nextRawToken, Duration.between(now, next.getExpiresAt()).toSeconds());
    }

    private String rejectReason(RefreshToken token) {
        if (token.isUsed()) {
            return "used";
        }
        return token.isRevoked() ? "revoked" : "expired";
    }

    // 응답은 원인을 나누지 않지만 서버 로그에는 남긴다. 원문 토큰은 로그에 쓰지 않는다.
    private CustomException rejected(String reason, Long tokenId) {
        log.warn("[RefreshToken] 재발급 거부 reason={} tokenId={}", reason, tokenId);
        return new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
}
