package com.ceos24.cgv.support;

import com.ceos24.cgv.domain.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 시나리오 테스트는 토큰을 JwtProvider에서 직접 꺼내지 않고 로그인 API 응답에서 받는다.
// 발급 경로(AuthenticationManager → 비밀번호 비교 → 토큰 생성)까지 한 번에 검증하기 위해서다.
public abstract class AuthScenarioTest extends ControllerIntegrationTest {

    protected static final String PASSWORD = "password1!";

    @Autowired private PasswordEncoder passwordEncoder;

    protected ResultActions signupRequest(String loginId, String extraJsonFields) throws Exception {
        String body = """
                {"loginId":"%s","password":"%s","name":"테스트","birthDate":"2000-01-01",
                 "email":"%s@test.com","phoneNumber":"01012345678"%s}
                """.formatted(loginId, PASSWORD, loginId, extraJsonFields);
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected Long signup(String loginId) throws Exception {
        String response = signupRequest(loginId, "")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.userId")).longValue();
    }

    protected ResultActions loginRequest(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password)));
    }

    protected String loginBody(String loginId) throws Exception {
        return loginRequest(loginId, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected String login(String loginId) throws Exception {
        return JsonPath.read(loginBody(loginId), "$.data.accessToken");
    }

    protected String loginForRefreshToken(String loginId) throws Exception {
        return JsonPath.read(loginBody(loginId), "$.data.refreshToken");
    }

    // 회원가입 API로는 관리자를 만들 수 없어 저장소에 직접 넣는다. 로그인은 API로 한다.
    protected User persistAdmin(String loginId) {
        return persist(User.createAdmin(loginId, passwordEncoder.encode(PASSWORD), "관리자",
                LocalDate.of(2000, 1, 1), loginId + "@test.com", "01012345678"));
    }

    protected RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }
}
