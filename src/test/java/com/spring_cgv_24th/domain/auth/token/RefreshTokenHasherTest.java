package com.spring_cgv_24th.domain.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshTokenHasherTest {

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();

    // 알려진 SHA-256 결과와 비교하여 저장할 해시가 정확한지 확인한다.
    @Test
    void hashesTokenWithSha256() {
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // 동일한 원문은 조회 가능한 동일 해시를 만들고 원문과는 다른 값으로 저장된다.
    @Test
    void hashesSameTokenDeterministically() {
        String token = "header.payload.signature";

        assertThat(hasher.hash(token)).isEqualTo(hasher.hash(token));
        assertThat(hasher.hash(token)).matches("[0-9a-f]{64}").isNotEqualTo(token);
    }

    // 다른 원문이나 공백을 덧붙인 원문을 같은 자격 증명으로 취급하지 않는다.
    @Test
    void doesNotNormalizeOrIgnoreTokenChanges() {
        assertThat(hasher.hash("token-one")).isNotEqualTo(hasher.hash("token-two"));
        assertThat(hasher.hash("token-one")).isNotEqualTo(hasher.hash(" token-one "));
    }

    // 같은 JWT 원문에서 계산한 해시만 저장된 해시와 일치한다.
    @Test
    void matchesOnlyCorrespondingTokenHash() {
        String token = "header.payload.signature";

        assertThat(hasher.matches(token, hasher.hash(token))).isTrue();
        assertThat(hasher.matches("other-token", hasher.hash(token))).isFalse();
    }

    // 해시를 삭제한 상태나 해시 대신 원문을 저장한 상태는 일치하지 않는다.
    @Test
    void rejectsMissingOrPlaintextStoredHash() {
        assertThat(hasher.matches("token", null)).isFalse();
        assertThat(hasher.matches("token", "token")).isFalse();
    }

    // null 또는 빈 문자열은 Refresh Token 자격 증명으로 사용할 수 없다.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsMissingOrBlankToken(String token) {
        CustomException error = assertThrows(CustomException.class, () -> hasher.hash(token));

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }
}
