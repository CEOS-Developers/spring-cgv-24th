package com.ceos24.cgv.domain.auth.service;

import com.ceos24.cgv.domain.auth.entity.RefreshToken;
import com.ceos24.cgv.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void replace(Long memberId, String refreshToken) {
        refreshTokenRepository.deleteByMemberId(memberId);
        refreshTokenRepository.save(new RefreshToken(refreshToken, memberId));
    }
}
