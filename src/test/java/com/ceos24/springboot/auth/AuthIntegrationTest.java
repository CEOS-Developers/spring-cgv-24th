package com.ceos24.springboot.auth;

import com.ceos24.springboot.auth.dto.LoginRequest;
import com.ceos24.springboot.auth.dto.LoginResponse;
import com.ceos24.springboot.auth.jwt.JwtProvider;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    private static final String EMAIL = "test@test.com";
    private static final String PASSWORD = "1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JsonMapper jsonMapper;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = User.create(
                "테스트유저",
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                "010-1234-5678"
        );

        userId = userRepository.saveAndFlush(user).getId();
    }

    //로그인 API의 정상 응답을 확인
    @Test
    void loginWithValidCredentialsReturnsTokenForUser() throws Exception {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        LoginResponse response = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                LoginResponse.class
        );

        assertThat(jwtProvider.getUserId(response.accessToken())).isEqualTo(userId);
    }

    // 비밀번호가 틀린 경우 401 반환 확인
    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest(EMAIL, "wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    // 회원가입되지 않은 이메일로 로그인 시도한 경우 401 반환
    @Test
    void loginWithUnknownEmailReturnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest("unknown@example.com", PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
}
