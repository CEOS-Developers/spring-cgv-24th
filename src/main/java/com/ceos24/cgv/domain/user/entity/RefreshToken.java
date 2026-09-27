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
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"),
        // 묶음 단위 폐기의 조회 조건이다. 인덱스가 없으면 InnoDB가 전체를 훑으며 지나간 행을 모두 잠근다.
        indexes = @Index(name = "idx_refresh_token_family", columnList = "family_id"))
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

    // 로그인 한 번에서 순환으로 이어진 토큰은 같은 값을 갖는다. 다른 로그인(다른 기기)과 구분하는 단위다.
    @Column(name = "family_id", nullable = false, columnDefinition = "char(36)")
    private String familyId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 이 토큰으로 다음 토큰을 받아 간 시각. 값이 있는 토큰이 다시 들어오면 두 곳에서 같은 토큰을 쓰고 있다는 뜻이다.
    private LocalDateTime usedAt;

    // 행을 지우지 않고 시각을 남긴다. 로그아웃한 토큰이 다시 들어오면 "없음"이 아니라 "폐기됨"으로 기록할 수 있다.
    private LocalDateTime revokedAt;

    @Builder
    private RefreshToken(User user, String tokenHash, String familyId, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    // 만료 시각을 물려받는다. 순환할 때마다 늘려 주면 토큰을 훔친 쪽이 계속 순환해 무기한 쓸 수 있다.
    public RefreshToken rotate(String nextTokenHash, LocalDateTime now) {
        this.usedAt = now;
        return RefreshToken.builder()
                .user(this.user)
                .tokenHash(nextTokenHash)
                .familyId(this.familyId)
                .expiresAt(this.expiresAt)
                .build();
    }

    public boolean isUsableAt(LocalDateTime now) {
        return !isUsed() && !isRevoked() && !isExpiredAt(now);
    }

    public boolean isUsed() {
        return this.usedAt != null;
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
