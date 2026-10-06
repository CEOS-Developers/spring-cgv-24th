package com.spring_cgv_24th.domain.member.entity;

import com.spring_cgv_24th.domain.member.enums.MemberRole;
import com.spring_cgv_24th.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_member_refresh_token_hash", columnNames = "refresh_token_hash")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 255)
    @ColumnDefault("'!DISABLED!'")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @ColumnDefault("'USER'")
    private MemberRole role;

    // 로그인 전 또는 로그아웃 후에는 비어 있고, 로그인할 때마다 최신 JWT의 해시로 교체한다.
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @Builder
    public Member(String email, String name, String passwordHash, MemberRole role) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.role = role == null ? MemberRole.USER : role;
    }

    public void replaceRefreshToken(String tokenHash) {
        if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Refresh Token 해시는 SHA-256 형식이어야 합니다.");
        }
        this.refreshTokenHash = tokenHash;
    }

    public void revokeRefreshToken() {
        this.refreshTokenHash = null;
    }
}
