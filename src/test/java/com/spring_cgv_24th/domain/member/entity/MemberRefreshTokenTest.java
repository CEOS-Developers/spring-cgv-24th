package com.spring_cgv_24th.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberRefreshTokenTest {

    private static final String TOKEN_HASH = "a".repeat(64);

    // 회원가입 직후에는 Refresh Token이 아직 발급되지 않아 해시가 비어 있다.
    @Test
    void newlyRegisteredMemberHasNoRefreshTokenHash() {
        assertThat(createMember().getRefreshTokenHash()).isNull();
    }

    // 회원에는 JWT 원문이 아닌 지정된 SHA-256 해시만 저장한다.
    @Test
    void storesRefreshTokenHash() {
        Member member = createMember();

        member.replaceRefreshToken(TOKEN_HASH);

        assertThat(member.getRefreshTokenHash()).isEqualTo(TOKEN_HASH);
    }

    // 새 로그인 발급 시 이전 해시를 최신 토큰의 해시로 대체한다.
    @Test
    void replacementOverwritesPreviousHash() {
        Member member = createMember();
        member.replaceRefreshToken(TOKEN_HASH);

        member.replaceRefreshToken("b".repeat(64));

        assertThat(member.getRefreshTokenHash()).isEqualTo("b".repeat(64));
    }

    // 로그아웃은 해시 삭제로 표현하며 반복 호출해도 삭제 상태가 유지된다.
    @Test
    void revocationClearsHashIdempotently() {
        Member member = createMember();
        member.replaceRefreshToken(TOKEN_HASH);

        member.revokeRefreshToken();
        member.revokeRefreshToken();

        assertThat(member.getRefreshTokenHash()).isNull();
    }

    // 로그아웃 후 다시 로그인하면 새 해시를 저장할 수 있다.
    @Test
    void canReplaceHashAfterRevocation() {
        Member member = createMember();
        member.replaceRefreshToken(TOKEN_HASH);
        member.revokeRefreshToken();

        member.replaceRefreshToken("b".repeat(64));

        assertThat(member.getRefreshTokenHash()).isEqualTo("b".repeat(64));
    }

    // JWT 원문이나 잘못된 해시로 기존에 저장된 정상 해시를 덮어쓰지 못하게 한다.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "header.payload.signature", "invalid-hash"})
    void rejectsInvalidHashWithoutChangingStoredState(String invalidHash) {
        Member member = createMember();
        member.replaceRefreshToken(TOKEN_HASH);

        assertThrows(IllegalArgumentException.class, () -> member.replaceRefreshToken(invalidHash));
        assertThat(member.getRefreshTokenHash()).isEqualTo(TOKEN_HASH);
    }

    private Member createMember() {
        return Member.builder()
                .email("member@example.com")
                .name("회원")
                .passwordHash("hashed-password")
                .build();
    }
}
