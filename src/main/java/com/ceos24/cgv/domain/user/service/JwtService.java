package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.entity.RefreshEntity;
import com.ceos24.cgv.domain.user.repository.RefreshRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class JwtService {

    private final RefreshRepository refreshRepository;

    // 소셜 로그인 성공 후 쿠키(Refresh) -> 헤더 방식으로 응답


    // Refresh 토큰으로 Access 토큰 재발급 로직


    // JWT Refresh token 발급 후 저장 메서드
    @Transactional
    public void addRefresh(String username, String refreshToken) {
        RefreshEntity refreshEntity = RefreshEntity.create(username, refreshToken);

        refreshRepository.save(refreshEntity);
    }

    // 존재 확인 메서드
    public Boolean existsRefresh(String refreshToken) {
        return refreshRepository.existsByRefresh(refreshToken);
    }

    // 토큰 삭제 메서드
    @Transactional
    public void removeRefresh(String refreshToken) {
        refreshRepository.deleteByRefresh(refreshToken);
    }

    // 특정 유저 토큰 모두 삭제(탈퇴)
    @Transactional
    public void removeRefreshUser(String username) {
        refreshRepository.deleteByUsername(username);
    }
}
