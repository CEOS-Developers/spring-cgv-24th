package com.ceos24.cgv.domain.auth.entity;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Long memberId;

    private boolean isUsed = false;

    private LocalDateTime usedAt;

    @Column(length = 512)
    private String reissuedAccessToken;

    @Column(length = 512)
    private String reissuedRefreshToken;

    @Builder
    public RefreshToken(String token, Long memberId) {
        this.token = token;
        this.memberId = memberId;
    }

    public void useToken(String newAccessToken, String newRefreshToken) {
        if (!isUsed) {
            this.isUsed = true;
            this.usedAt = LocalDateTime.now();
            this.reissuedAccessToken = newAccessToken;
            this.reissuedRefreshToken = newRefreshToken;
        }
    }

    public boolean isWithinGracePeriod(Duration gracePeriod) {
        if (this.usedAt == null) {
            return false;
        }
        return Duration.between(this.usedAt, LocalDateTime.now()).compareTo(gracePeriod) <= 0;
    }
}
