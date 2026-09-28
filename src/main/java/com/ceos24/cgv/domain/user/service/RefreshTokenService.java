package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.entity.RefreshEntity;
import com.ceos24.cgv.domain.user.repository.RefreshRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshRepository refreshRepository;

    @Transactional
    public void addRefresh(String username, String refreshToken) {
        RefreshEntity refreshEntity = RefreshEntity.create(username, refreshToken);

        refreshRepository.save(refreshEntity);
    }

    public boolean existsRefresh(String refreshToken) {
        return refreshRepository.existsByRefresh(refreshToken);
    }

    @Transactional
    public void removeRefresh(String refreshToken) {
        refreshRepository.deleteByRefresh(refreshToken);
    }

    @Transactional
    public void removeRefreshUser(String username) {
        refreshRepository.deleteByUsername(username);
    }
}
