package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.user.entity.RefreshToken;
import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.RefreshTokenRepository;
import com.ceos24.cgv.global.security.jwt.JwtProperties;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import com.ceos24.cgv.support.AuthScenarioTest;
import com.ceos24.cgv.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 리프레시 토큰 시나리오. 세션 4와 같이 로그인 API로 받은 토큰을 실제 필터 체인에 태운다.
class RefreshTokenScenarioTest extends AuthScenarioTest {

    private static final String PROTECTED_API = "/api/reservations";
    private static final String REISSUE_API = "/api/auth/reissue";

    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired RefreshTokenProvider refreshTokenProvider;
    @Autowired RefreshTokenProperties refreshTokenProperties;
    @Autowired JwtProvider jwtProvider;
    @Autowired JwtProperties jwtProperties;

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

    // ─── 재발급 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 리프레시 토큰으로 재발급하면 새 액세스 토큰으로 보호 API를 정상 호출할 수 있다")
    void 유효한_리프레시_토큰으로_재발급한_토큰으로_보호_API를_호출할_수_있다() throws Exception {
        Long userId = signup("refresh01");
        String refreshToken = loginForRefreshToken("refresh01");

        String body = reissueRequest(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(jwtProperties.accessTokenValidity().toSeconds()))
                // 순환 발급 전이라 리프레시 토큰은 다시 주지 않는다
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String reissued = JsonPath.read(body, "$.data.accessToken");

        assertThat(jwtProvider.parse(reissued)).isEqualTo(new AuthUser(userId, Role.USER));
        mockMvc.perform(get(PROTECTED_API).with(bearer(reissued)))
                .andExpect(status().isOk());
    }

    // 실제 재발급 상황은 액세스 토큰이 만료된 뒤다. 클라이언트가 만료된 토큰을 헤더에 남겨 둔 채 불러도 막히면 안 된다.
    @Test
    @DisplayName("만료된 액세스 토큰을 헤더에 단 채로도 재발급할 수 있다")
    void 만료된_액세스_토큰을_헤더에_단_채로도_재발급할_수_있다() throws Exception {
        Long userId = signup("refresh01");
        String refreshToken = loginForRefreshToken("refresh01");

        reissueRequest(refreshToken, expiredAccessToken(userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    @DisplayName("재발급한 액세스 토큰의 권한은 리프레시 토큰이 아니라 DB의 사용자에서 정해진다")
    void 재발급한_액세스_토큰의_권한은_DB의_사용자에서_정해진다() throws Exception {
        User admin = persistAdmin("admin01");
        String refreshToken = storeRefreshToken(admin, LocalDateTime.now().plusDays(1));

        String body = reissueRequest(refreshToken)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reissued = JsonPath.read(body, "$.data.accessToken");

        assertThat(jwtProvider.parse(reissued).role()).isEqualTo(Role.ADMIN);
        mockMvc.perform(get("/api/admin/check").with(bearer(reissued)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("만료된 리프레시 토큰으로 재발급하면 401 REFRESH_TOKEN_INVALID")
    void 만료된_리프레시_토큰이면_401_REFRESH_TOKEN_INVALID() throws Exception {
        User user = persist(TestFixtures.user("expired01"));
        // 만료 시각을 과거로 둔 행을 직접 저장한다. 시간이 지나기를 기다리지 않는다.
        String refreshToken = storeRefreshToken(user, LocalDateTime.now().minusMinutes(1));

        reissueRequest(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않거나 임의로 만든 리프레시 토큰으로 재발급하면 401 REFRESH_TOKEN_INVALID")
    void 존재하지_않거나_임의로_만든_리프레시_토큰이면_401_REFRESH_TOKEN_INVALID() throws Exception {
        // 형식은 진짜와 같지만 발급된 적 없는 토큰
        reissueRequest(refreshTokenProvider.generate())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        // 형식부터 다른 임의 문자열
        reissueRequest("made-up-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("액세스 토큰을 재발급 API 본문에 넣으면 401 REFRESH_TOKEN_INVALID")
    void 액세스_토큰을_재발급_본문에_넣으면_401_REFRESH_TOKEN_INVALID() throws Exception {
        signup("refresh01");
        String accessToken = login("refresh01");

        reissueRequest(accessToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("만료·미발급·액세스 토큰으로 거부된 응답은 본문까지 모두 같다")
    void 재발급_거부_응답은_원인과_관계없이_같다() throws Exception {
        User user = persist(TestFixtures.user("expired01"));
        String expired = storeRefreshToken(user, LocalDateTime.now().minusMinutes(1));
        signup("refresh01");
        String accessToken = login("refresh01");

        String expiredBody = rejectedBody(expired);
        assertThat(rejectedBody(refreshTokenProvider.generate())).isEqualTo(expiredBody);
        assertThat(rejectedBody(accessToken)).isEqualTo(expiredBody);
    }

    @Test
    @DisplayName("리프레시 토큰이 비어 있거나 빠지면 400 INVALID_INPUT_VALUE")
    void 리프레시_토큰이_비어_있으면_400() throws Exception {
        reissueRequest("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        mockMvc.perform(post(REISSUE_API).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private ResultActions reissueRequest(String refreshToken) throws Exception {
        return mockMvc.perform(post(REISSUE_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private ResultActions reissueRequest(String refreshToken, String accessTokenInHeader) throws Exception {
        return mockMvc.perform(post(REISSUE_API).with(bearer(accessTokenInHeader))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private String rejectedBody(String refreshToken) throws Exception {
        return reissueRequest(refreshToken)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }

    // 로그인 API를 거치지 않고 원하는 만료 시각의 토큰을 만든다. 저장은 운영 코드와 같이 해시로 한다.
    private String storeRefreshToken(User user, LocalDateTime expiresAt) {
        String rawToken = refreshTokenProvider.generate();
        persist(RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenProvider.hash(rawToken))
                .expiresAt(expiresAt)
                .build());
        flushAndClear();
        return rawToken;
    }

    private String expiredAccessToken(Long userId) {
        Instant issuedAt = Instant.now().minus(jwtProperties.accessTokenValidity()).minus(Duration.ofMinutes(1));
        return new JwtProvider(jwtProperties, Clock.fixed(issuedAt, ZoneOffset.UTC))
                .createAccessToken(userId, Role.USER);
    }

    private List<RefreshToken> tokensOf(Long userId) {
        return refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(userId))
                .toList();
    }
}
