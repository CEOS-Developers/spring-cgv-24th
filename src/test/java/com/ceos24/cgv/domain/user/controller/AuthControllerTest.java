package com.ceos24.cgv.domain.user.controller;

import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends ControllerIntegrationTest {

    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void 회원가입하면_비밀번호를_해시로_저장하고_USER로_생성한다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validSignup())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.loginId").value("cgvuser01"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        User saved = findUser("cgvuser01");
        assertThat(saved.getPassword()).isNotEqualTo("password1!");
        assertThat(passwordEncoder.matches("password1!", saved.getPassword())).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getPhoneNumber()).isEqualTo("01012345678");
    }

    @Test
    void 본문에_role_ADMIN을_넣어도_USER로_생성된다() throws Exception {
        Map<String, String> body = validSignup();
        body.put("role", "ADMIN");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated());

        assertThat(findUser("cgvuser01").getRole()).isEqualTo(Role.USER);
    }

    @Test
    void 같은_비밀번호도_해시값은_매번_다르다() {
        String first = passwordEncoder.encode("password1!");
        String second = passwordEncoder.encode("password1!");

        assertThat(first).isNotEqualTo(second);
        assertThat(passwordEncoder.matches("password1!", first)).isTrue();
        assertThat(passwordEncoder.matches("password1!", second)).isTrue();
    }

    @Test
    void 이미_있는_아이디로_가입하면_409() throws Exception {
        persist(TestFixtures.user("cgvuser01"));
        flushAndClear();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validSignup())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
            "loginId, CgvUser01",          // 대문자
            "loginId, abc",                // 3자
            "loginId, cgv_user",           // 특수문자
            "password, short1!",           // 7자
            "password, 비밀번호비밀번호1!",    // 비ASCII
            "password, 'pass word1!'",     // 공백
            "email, not-an-email",
            "phoneNumber, 010-1234-5678",  // 하이픈
            "phoneNumber, 0212345678",     // 휴대전화 아님
            "birthDate, 2999-01-01",       // 미래
            "name, ''",
    })
    void 형식이_틀리면_400(String field, String value) throws Exception {
        Map<String, String> body = validSignup();
        body.put(field, value);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
    }

    @Test
    void 필수값이_빠지면_400() throws Exception {
        Map<String, String> body = validSignup();
        body.remove("phoneNumber");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phoneNumber"));
    }

    @Test
    void 날짜로_읽을_수_없는_생년월일은_500이_아니라_400() throws Exception {
        Map<String, String> body = validSignup();
        body.put("birthDate", "2000-13-01");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    private Map<String, String> validSignup() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("loginId", "cgvuser01");
        body.put("password", "password1!");
        body.put("name", "홍길동");
        body.put("birthDate", "2000-01-01");
        body.put("email", "cgv@example.com");
        body.put("phoneNumber", "01012345678");
        return body;
    }

    private String json(Map<String, String> body) {
        return body.entrySet().stream()
                .map(e -> "\"%s\":\"%s\"".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private User findUser(String loginId) {
        return em.createQuery("select u from User u where u.loginId = :loginId", User.class)
                .setParameter("loginId", loginId)
                .getSingleResult();
    }
}
