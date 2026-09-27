package com.ceos24.cgv.domain.user.entity;

import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 1, 15, 0, 0);

    private final RefreshToken token = RefreshToken.builder()
            .user(TestFixtures.user("tokenuser"))
            .tokenHash("a".repeat(64))
            .expiresAt(EXPIRES_AT)
            .build();

    @Test
    @DisplayName("만료 전이고 폐기되지 않았으면 쓸 수 있다")
    void 만료_전이고_폐기되지_않았으면_쓸_수_있다() {
        assertThat(token.isUsableAt(EXPIRES_AT.minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("만료 시각과 같거나 지나면 쓸 수 없다")
    void 만료_시각이_되면_쓸_수_없다() {
        assertThat(token.isUsableAt(EXPIRES_AT)).isFalse();
        assertThat(token.isExpiredAt(EXPIRES_AT)).isTrue();
    }

    @Test
    @DisplayName("폐기하면 만료 전이어도 쓸 수 없다")
    void 폐기하면_만료_전이어도_쓸_수_없다() {
        token.revoke(EXPIRES_AT.minusDays(1));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isUsableAt(EXPIRES_AT.minusDays(1))).isFalse();
    }

    @Test
    @DisplayName("다시 폐기해도 처음 폐기한 시각이 유지된다")
    void 다시_폐기해도_처음_시각이_유지된다() {
        LocalDateTime first = EXPIRES_AT.minusDays(2);
        token.revoke(first);
        token.revoke(EXPIRES_AT.minusDays(1));

        assertThat(token.getRevokedAt()).isEqualTo(first);
    }
}
