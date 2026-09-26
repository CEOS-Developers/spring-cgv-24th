package com.ceos.cgv.domain.auth.service;

import com.ceos.cgv.domain.auth.dto.SignupRequest;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RegistrationConcurrencyIntegrationTest {
    private static final String LOGIN_ID = "race_member";

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void 동시에_같은_아이디로_가입해도_회원은_한명만_생성된다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<User> first = executor.submit(() -> {
                start.await();
                return registrationService.register(new SignupRequest(
                        "Race_Member", "첫 회원", "race-one@example.com", "Password123!"));
            });
            Future<User> second = executor.submit(() -> {
                start.await();
                return registrationService.register(new SignupRequest(
                        LOGIN_ID, "둘째 회원", "race-two@example.com", "Password123!"));
            });

            start.countDown();
            int success = 0;
            int conflict = 0;
            for (Future<User> result : List.of(first, second)) {
                try {
                    assertThat(result.get(10, TimeUnit.SECONDS).getLoginId()).isEqualTo(LOGIN_ID);
                    success++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
                    ErrorCode code = ((BusinessException) exception.getCause()).getErrorCode();
                    assertThat(code).isIn(
                            ErrorCode.LOGIN_ID_ALREADY_EXISTS, ErrorCode.ACCOUNT_ALREADY_EXISTS);
                    conflict++;
                }
            }

            assertThat(success).isEqualTo(1);
            assertThat(conflict).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE login_id = ?",
                    Integer.class, LOGIN_ID)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM users WHERE login_id = ?", LOGIN_ID);
    }
}
