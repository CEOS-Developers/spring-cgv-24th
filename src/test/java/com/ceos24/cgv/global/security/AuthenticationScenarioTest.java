package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.global.security.jwt.JwtProperties;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import com.ceos24.cgv.support.AuthScenarioTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 과제의 인증 시나리오 표를 그대로 옮긴 테스트. 토큰은 로그인 API로 받거나,
// 실패 토큰이 필요하면 실제 JwtProvider를 다른 설정으로 만들어 발급한다.
class AuthenticationScenarioTest extends AuthScenarioTest {

    private static final String PUBLIC_API = "/api/movies";
    private static final String PROTECTED_API = "/api/reservations";

    @Autowired JwtProvider jwtProvider;
    @Autowired JwtProperties jwtProperties;

    @Test
    @DisplayName("올바른 로그인 정보면 Access Token을 발급한다")
    void 올바른_로그인_정보면_Access_Token을_발급한다() throws Exception {
        Long userId = signup("scenario01");

        String response = loginRequest("scenario01", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(jwtProperties.accessTokenValidity().toSeconds()))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(response, "$.data.accessToken");
        assertThat(jwtProvider.parse(token)).isEqualTo(new AuthUser(userId, Role.USER));
    }

    @Test
    @DisplayName("없는 계정과 틀린 비밀번호는 status·code·message까지 같은 실패 응답을 받고 토큰이 발급되지 않는다")
    void 없는_계정과_틀린_비밀번호의_로그인_실패_응답이_완전히_같다() throws Exception {
        signup("scenario01");

        MockHttpServletResponse wrongPassword = loginRequest("scenario01", "wrongpass1!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse();
        MockHttpServletResponse missingAccount = loginRequest("nosuchuser", "wrongpass1!")
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse();

        String wrongBody = wrongPassword.getContentAsString();
        String missingBody = missingAccount.getContentAsString();
        assertThat(missingAccount.getStatus()).isEqualTo(wrongPassword.getStatus());
        assertThat((String) JsonPath.read(missingBody, "$.code")).isEqualTo(JsonPath.read(wrongBody, "$.code"));
        assertThat((String) JsonPath.read(missingBody, "$.message")).isEqualTo(JsonPath.read(wrongBody, "$.message"));
        // 필드별 비교만으로는 나중에 한쪽에만 추가되는 필드를 놓친다
        assertThat(missingBody).isEqualTo(wrongBody);
    }

    @Test
    @DisplayName("토큰 없이 공개 API를 호출하면 정상 처리된다")
    void 토큰_없이_공개_API를_호출하면_정상_처리된다() throws Exception {
        mockMvc.perform(get(PUBLIC_API))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("로그인으로 받은 토큰으로 보호된 API를 호출하면 정상 처리된다")
    void 로그인으로_받은_토큰으로_보호된_API를_호출하면_정상_처리된다() throws Exception {
        signup("scenario01");
        String token = login("scenario01");

        mockMvc.perform(get(PROTECTED_API).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("토큰 없이 보호된 API를 호출하면 401 TOKEN_NOT_EXIST 공통 JSON을 받는다")
    void 토큰_없이_보호된_API를_호출하면_401_TOKEN_NOT_EXIST() throws Exception {
        mockMvc.perform(get(PROTECTED_API))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"))
                .andExpect(jsonPath("$.message").value("인증 토큰이 없습니다."));
    }

    @Test
    @DisplayName("만료된 토큰으로 보호된 API를 호출하면 401 TOKEN_EXPIRED")
    void 만료된_토큰이면_401_TOKEN_EXPIRED() throws Exception {
        Long userId = signup("scenario01");

        mockMvc.perform(get(PROTECTED_API).with(bearer(expiredToken(userId))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("만료된 토큰입니다."));
    }

    @Test
    @DisplayName("payload를 USER→ADMIN으로 바꾼 토큰은 관리자 권한을 얻지 못하고 401 TOKEN_INVALID")
    void payload를_변조한_토큰이면_401_TOKEN_INVALID() throws Exception {
        signup("scenario01");
        String forged = withRole(login("scenario01"), Role.ADMIN);

        // 200이면 위조 권한이 통했고, 403이면 서명 검증 없이 claim을 읽은 것이다
        mockMvc.perform(get("/api/admin/check").with(bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));
    }

    @Test
    @DisplayName("claim은 정상이지만 다른 키로 서명한 토큰이면 401 TOKEN_INVALID")
    void 다른_키로_서명한_토큰이면_401_TOKEN_INVALID() throws Exception {
        Long userId = signup("scenario01");

        mockMvc.perform(get(PROTECTED_API).with(bearer(otherKeyToken(userId))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("정상 인증 요청 직후 토큰 없이 보호된 API를 호출하면 401 (이전 요청의 인증이 유지되지 않는다)")
    void 정상_인증_요청_직후_토큰_없는_요청은_401() throws Exception {
        signup("scenario01");
        String token = login("scenario01");

        MvcResult first = mockMvc.perform(get(PROTECTED_API).with(bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        // MockMvc는 모든 요청을 이 테스트 스레드에서 처리한다. 요청 끝에 컨텍스트를 비우지 않으면 다음 요청이 물려받는다.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // 브라우저처럼 앞 응답의 쿠키·세션을 그대로 실어 보낸다. MockMvc는 이것들을 자동으로 이어 주지 않는다.
        MockHttpServletRequestBuilder next = get(PROTECTED_API);
        Cookie[] cookies = first.getResponse().getCookies();
        if (cookies.length > 0) {
            next.cookie(cookies);
        }
        HttpSession session = first.getRequest().getSession(false);
        if (session != null) {
            next.session((MockHttpSession) session);
        }

        mockMvc.perform(next)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        assertThat(cookies).isEmpty();
        assertThat(session).isNull();
    }

    @Test
    @DisplayName("공개 API는 변조된 토큰을 보내도 익명으로 정상 처리된다")
    void 공개_API는_변조_토큰을_보내도_익명으로_정상_처리된다() throws Exception {
        signup("scenario01");
        String forged = withRole(login("scenario01"), Role.ADMIN);

        mockMvc.perform(get(PUBLIC_API).with(bearer(forged)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // 같은 키, 같은 발급 코드. 발급 시각만 유효기간보다 1분 더 과거로 둬 exp가 이미 지난 토큰을 만든다.
    private String expiredToken(Long userId) {
        Instant issuedAt = Instant.now().minus(jwtProperties.accessTokenValidity()).minus(Duration.ofMinutes(1));
        return new JwtProvider(jwtProperties, Clock.fixed(issuedAt, ZoneOffset.UTC))
                .createAccessToken(userId, Role.USER);
    }

    // 키만 다르고 sub·role·iss·exp는 정상 토큰과 같다. 서버가 "자기 키로 서명됐는지"를 보는지만 가른다.
    private String otherKeyToken(Long userId) {
        String otherSecret = Base64.getEncoder().encodeToString(
                "another-signer-dummy-hs256-key-for-test-only!!".getBytes(StandardCharsets.UTF_8));
        JwtProperties otherKey = new JwtProperties(otherSecret, jwtProperties.accessTokenValidity());
        return new JwtProvider(otherKey, Clock.systemUTC()).createAccessToken(userId, Role.USER);
    }

    // 헤더와 서명은 원본 그대로 두고 payload만 바꾼다. 서명은 원래 payload에 대한 것이라 맞지 않게 된다.
    private String withRole(String token, Role role) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tampered = payload.replace("\"role\":\"USER\"", "\"role\":\"" + role.name() + "\"");
        assertThat(tampered).isNotEqualTo(payload);

        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tampered.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + encoded + "." + parts[2];
    }
}
