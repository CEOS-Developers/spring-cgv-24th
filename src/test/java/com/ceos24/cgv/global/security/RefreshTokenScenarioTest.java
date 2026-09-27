package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.user.entity.RefreshToken;
import com.ceos24.cgv.domain.user.repository.RefreshTokenRepository;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import com.ceos24.cgv.support.AuthScenarioTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 리프레시 토큰 시나리오. 세션 4와 같이 로그인 API로 받은 토큰을 실제 필터 체인에 태운다.
class RefreshTokenScenarioTest extends AuthScenarioTest {

    private static final String PROTECTED_API = "/api/reservations";

    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired RefreshTokenProvider refreshTokenProvider;
    @Autowired RefreshTokenProperties refreshTokenProperties;
    @Autowired JwtProvider jwtProvider;

    @Test
    @DisplayName("로그인하면 액세스 토큰과 리프레시 토큰을 함께 발급한다")
    void 로그인하면_액세스_토큰과_리프레시_토큰을_함께_발급한다() throws Exception {
        Long userId = signup("refresh01");

        String body = loginRequest("refresh01", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.refreshTokenExpiresIn")
                        .value(refreshTokenProperties.validity().toSeconds()))
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(body, "$.data.accessToken");
        String refreshToken = JsonPath.read(body, "$.data.refreshToken");
        assertThat(jwtProvider.parse(accessToken).userId()).isEqualTo(userId);
        assertThat(refreshToken).hasSize(43).isNotEqualTo(accessToken);
    }

    @Test
    @DisplayName("DB에는 리프레시 토큰 원문이 아니라 SHA-256 해시가 저장된다")
    void DB에는_리프레시_토큰_원문이_아니라_해시가_저장된다() throws Exception {
        Long userId = signup("refresh01");
        LocalDateTime before = LocalDateTime.now();
        String refreshToken = loginForRefreshToken("refresh01");
        LocalDateTime after = LocalDateTime.now();
        flushAndClear();

        List<RefreshToken> saved = tokensOf(userId);
        assertThat(saved).hasSize(1);
        RefreshToken token = saved.getFirst();
        assertThat(token.getTokenHash())
                .isNotEqualTo(refreshToken)
                .isEqualTo(refreshTokenProvider.hash(refreshToken));
        assertThat(refreshTokenRepository.findByTokenHash(refreshToken)).isEmpty();
        assertThat(token.getExpiresAt())
                .isBetween(before.plus(refreshTokenProperties.validity()), after.plus(refreshTokenProperties.validity()));
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("로그인할 때마다 새 리프레시 토큰이 생겨 기기별로 따로 유지된다")
    void 로그인할_때마다_새_리프레시_토큰이_생긴다() throws Exception {
        Long userId = signup("refresh01");

        String first = loginForRefreshToken("refresh01");
        String second = loginForRefreshToken("refresh01");
        flushAndClear();

        assertThat(first).isNotEqualTo(second);
        assertThat(tokensOf(userId)).hasSize(2).noneMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("로그인에 실패하면 리프레시 토큰을 저장하지 않는다")
    void 로그인에_실패하면_리프레시_토큰을_저장하지_않는다() throws Exception {
        Long userId = signup("refresh01");

        loginRequest("refresh01", "wrongpass1!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").doesNotExist());
        flushAndClear();

        assertThat(tokensOf(userId)).isEmpty();
    }

    // 리프레시 토큰에는 점이 없어 JWT 세 조각으로 나뉘지 않는다. 필터가 형식 오류로 기록하고 보호 경로가 거부한다.
    @Test
    @DisplayName("리프레시 토큰을 Authorization 헤더에 넣어 보호 API를 호출하면 401 TOKEN_INVALID")
    void 리프레시_토큰을_Authorization_헤더에_넣으면_401_TOKEN_INVALID() throws Exception {
        signup("refresh01");
        String refreshToken = loginForRefreshToken("refresh01");

        mockMvc.perform(get(PROTECTED_API).with(bearer(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    private List<RefreshToken> tokensOf(Long userId) {
        return refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(userId))
                .toList();
    }
}
