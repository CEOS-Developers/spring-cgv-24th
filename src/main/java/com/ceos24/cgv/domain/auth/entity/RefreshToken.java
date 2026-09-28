package com.ceos24.cgv.domain.auth.entity;

import jakarta.persistence.*;
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

    @Builder
    public RefreshToken(String token, Long memberId) {
        this.token = token;
        this.memberId = memberId;
    }

    public void useToken() {
        if (!isUsed) {
            isUsed = true;
        }
    }
}
