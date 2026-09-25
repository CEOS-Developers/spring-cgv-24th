package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 러너는 컨텍스트 기동 시 테스트 트랜잭션 밖에서 커밋한다. 그래서 @Transactional 대신 끝나고 직접 지운다.
@SpringBootTest(properties = {
        "admin.login-id=" + AdminAccountInitializerTest.LOGIN_ID,
        "admin.password=" + AdminAccountInitializerTest.PASSWORD
})
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAccountInitializerTest {

    // 테스트 전용 값. 실제 관리자 비밀번호와 무관하다.
    static final String LOGIN_ID = "testadmin";
    static final String PASSWORD = "admin-test-only-pw1";

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminAccountInitializer initializer;
    @Autowired WebApplicationContext wac;

    @AfterAll
    void cleanUp() {
        userRepository.findByLoginId(LOGIN_ID).ifPresent(userRepository::delete);
    }

    @Test
    void 기동하면_관리자가_해시된_비밀번호로_생성된다() {
        User admin = userRepository.findByLoginId(LOGIN_ID).orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPassword())).isTrue();
    }

    @Test
    void 다시_실행해도_관리자는_하나다() throws Exception {
        initializer.run(new DefaultApplicationArguments());

        assertThat(userRepository.findAll()).filteredOn(u -> u.getLoginId().equals(LOGIN_ID)).hasSize(1);
    }

    @Test
    void 비밀번호가_비어_있으면_기동이_실패한다() {
        contextRunner()
                .withPropertyValues("spring.profiles.active=local", "admin.login-id=admin", "admin.password=")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .hasStackTraceContaining("on field 'password'"));
    }

    @Test
    void local_프로필이_아니면_러너가_뜨지_않고_설정값도_필요_없다() {
        contextRunner()
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AdminAccountInitializer.class);
                });
    }

    @Test
    void 관리자로_로그인한_토큰으로_관리자_API를_호출할_수_있다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.accessToken");

        mockMvc.perform(get("/api/admin/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    // 러너와 설정 바인딩만 띄운다. 전체 컨텍스트를 새로 올리지 않고 프로필·검증 조건만 본다.
    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(AdminAccountInitializer.class)
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));
    }
}
