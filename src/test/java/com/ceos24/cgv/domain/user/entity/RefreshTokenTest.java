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
            .familyId("family-1")
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
    @DisplayName("순환하면 기존 토큰은 사용 완료되어 만료 전이어도 쓸 수 없다")
    void 순환하면_기존_토큰은_사용_완료된다() {
        LocalDateTime now = EXPIRES_AT.minusDays(1);
        token.rotate("b".repeat(64), now);

        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(now);
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isUsableAt(now)).isFalse();
    }

    // 새 토큰의 만료를 순환 시각 기준으로 늘리면, 훔친 쪽이 계속 순환해 무기한 쓸 수 있다.
    @Test
    @DisplayName("순환으로 만든 새 토큰은 같은 사용자·묶음·만료 시각을 물려받고 아직 쓰지 않은 상태다")
    void 새_토큰은_묶음과_만료_시각을_물려받는다() {
        LocalDateTime now = EXPIRES_AT.minusDays(1);
        RefreshToken next = token.rotate("b".repeat(64), now);

        assertThat(next.getUser()).isSameAs(token.getUser());
        assertThat(next.getTokenHash()).isEqualTo("b".repeat(64));
        assertThat(next.getFamilyId()).isEqualTo("family-1");
        assertThat(next.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(next.isUsableAt(now)).isTrue();
    }
}
