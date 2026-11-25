package com.ceos.cgv.domain.auth.service;

import com.ceos.cgv.domain.auth.dto.SignupRequest;
import com.ceos.cgv.support.DatabaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class RegistrationTransactionIntegrationTest extends DatabaseIntegrationTest {
    private static final String LOGIN_ID = "tx_boundary_member";

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void 비밀번호_해시는_DB_트랜잭션_밖에서_수행되어_커넥션을_점유하지_않는다() {
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        AtomicInteger encodeCalls = new AtomicInteger();
        doAnswer(invocation -> {
            encodeCalls.incrementAndGet();
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(passwordEncoder).encode(any());

        registrationService.register(new SignupRequest(
                LOGIN_ID, "트랜잭션 경계 회원", "tx-boundary@example.com", "Password123!"));

        assertThat(encodeCalls).hasValue(1);
        assertThat(transactionActive).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE login_id = ?", Integer.class, LOGIN_ID)).isEqualTo(1);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM users WHERE login_id = ?", LOGIN_ID);
    }
}
