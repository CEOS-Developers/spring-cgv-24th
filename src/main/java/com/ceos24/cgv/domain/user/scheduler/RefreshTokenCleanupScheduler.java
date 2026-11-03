package com.ceos24.cgv.domain.user.scheduler;

import com.ceos24.cgv.domain.user.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    // 매시간 정각에 실행
    @Scheduled(cron = "0 0 * * * *")
    public void removeExpiredTokens() {
        refreshTokenService.removeExpiredTokens(
                LocalDateTime.now()
        );
    }
}