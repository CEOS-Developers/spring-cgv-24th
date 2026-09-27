package com.ceos24.cgv.domain.user.entity;

import com.ceos24.cgv.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 원문은 저장하지 않는다. DB가 유출돼도 해시만으로는 재발급을 요청할 수 없다.
@Getter
@Entity
@Table(name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // SHA-256 hex는 항상 64자다. 받은 토큰을 같은 방식으로 해시해 이 컬럼으로 바로 찾는다.
    @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 행을 지우지 않고 시각을 남긴다. 로그아웃한 토큰이 다시 들어오면 "없음"이 아니라 "폐기됨"으로 기록할 수 있다.
    private LocalDateTime revokedAt;

    @Builder
    private RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsableAt(LocalDateTime now) {
        return !isRevoked() && !isExpiredAt(now);
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(this.expiresAt);
    }

    // 같은 토큰으로 로그아웃이 여러 번 와도 처음 폐기한 시각을 유지한다.
    public void revoke(LocalDateTime now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
