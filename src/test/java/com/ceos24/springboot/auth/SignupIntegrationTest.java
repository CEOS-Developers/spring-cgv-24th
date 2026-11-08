package com.ceos24.springboot.auth;

import com.ceos24.springboot.auth.dto.LoginRequest;
import com.ceos24.springboot.auth.dto.LoginResponse;
import com.ceos24.springboot.auth.dto.SignupRequest;
import com.ceos24.springboot.auth.dto.SignupResponse;
import com.ceos24.springboot.auth.jwt.JwtProvider;
import com.ceos24.springboot.user.domain.Role;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SignupIntegrationTest {

    private static final String PASSWORD = "Test1234!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void signupCreatesUserWithHashedPasswordAndAllowsLogin() throws Exception {
        String email = "signup-user@example.com";

        String body = postJson("/api/auth/signup", signupRequest(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        SignupResponse response = jsonMapper.readValue(body, SignupResponse.class);
        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(response.userId()).isEqualTo(saved.getId());
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPassword())).isTrue();

        String token = login(email);
        assertThat(jwtProvider.getUserId(token)).isEqualTo(saved.getId());
        mockMvc.perform(get("/api/admin/check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void existingAdminCanCreateAdminWhoCanLoginAndAccessAdminApi() throws Exception {
        String initialEmail = "initial-admin@example.com";
        userRepository.saveAndFlush(User.createAdmin(
                "초기관리자", initialEmail,
                passwordEncoder.encode(PASSWORD), "010-1234-5678"
        ));
        String initialToken = login(initialEmail);
        String newEmail = "new-admin@example.com";

        mockMvc.perform(post("/api/admin/signup")
                        .header("Authorization", "Bearer " + initialToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest(newEmail))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User saved = userRepository.findByEmail(newEmail).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPassword())).isTrue();

        String token = login(newEmail);
        assertThat(jwtProvider.getUserId(token)).isEqualTo(saved.getId());
        mockMvc.perform(get("/api/admin/check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCannotCreateAdmin() throws Exception {
        String email = "anonymous-admin@example.com";

        postJson("/api/admin/signup", signupRequest(email))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));

        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    @Test
    void regularUserCannotCreateAdmin() throws Exception {
        String userEmail = "regular-user@example.com";
        postJson("/api/auth/signup", signupRequest(userEmail))
                .andExpect(status().isCreated());
        String token = login(userEmail);
        String adminEmail = "forbidden-admin@example.com";

        mockMvc.perform(post("/api/admin/signup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest(adminEmail))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(userRepository.existsByEmail(adminEmail)).isFalse();
    }

    @Test
    void duplicateEmailReturnsConflictWithoutCreatingAnotherAccount() throws Exception {
        SignupRequest request = signupRequest("duplicate@example.com");
        postJson("/api/auth/signup", request).andExpect(status().isCreated());
        long count = userRepository.count();

        postJson("/api/auth/signup", request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));

        assertThat(userRepository.count()).isEqualTo(count);
    }

    @ParameterizedTest(name = "invalid signup: {0}")
    @MethodSource("invalidSignupRequests")
    void invalidSignupReturnsBadRequestWithoutSaving(
            String field, SignupRequest request
    ) throws Exception {
        long count = userRepository.count();

        postJson("/api/auth/signup", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString(field + ":")));

        assertThat(userRepository.count()).isEqualTo(count);
    }

    @ParameterizedTest(name = "invalid login: {0}, email={1}")
    @MethodSource("invalidLoginRequests")
    void invalidLoginReturnsBadRequest(String field, String email, String password)
            throws Exception {
        postJson("/api/auth/login", new LoginRequest(email, password))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString(field + ":")))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    private static Stream<Arguments> invalidSignupRequests() {
        String email = "invalid-signup@example.com";
        String phone = "010-1234-5678";
        return Stream.of(
                Arguments.of("name", new SignupRequest(" ", email, PASSWORD, phone)),
                Arguments.of("email", new SignupRequest("테스트", "", PASSWORD, phone)),
                Arguments.of("email", new SignupRequest("테스트", "invalid", PASSWORD, phone)),
                Arguments.of("password", new SignupRequest("테스트", email, "", phone)),
                Arguments.of("password", new SignupRequest("테스트", email, "short", phone)),
                Arguments.of("password", new SignupRequest("테스트", email, "x".repeat(21), phone)),
                Arguments.of("phone", new SignupRequest("테스트", email, PASSWORD, " "))
        );
    }

    private static Stream<Arguments> invalidLoginRequests() {
        return Stream.of(
                Arguments.of("email", "invalid", PASSWORD),
                Arguments.of("email", "", PASSWORD),
                Arguments.of("password", "login@example.com", " "),
                Arguments.of("password", "login@example.com", null)
        );
    }

    private SignupRequest signupRequest(String email) {
        return new SignupRequest("가입테스트", email, PASSWORD, "010-1234-5678");
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private String login(String email) throws Exception {
        String body = postJson("/api/auth/login", new LoginRequest(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readValue(body, LoginResponse.class).accessToken();
    }
}
