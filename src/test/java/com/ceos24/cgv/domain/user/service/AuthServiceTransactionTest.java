package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.LoginRequest;
import com.ceos24.cgv.domain.user.dto.SignupRequest;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

// BCrypt는 요청 하나가 CPU를 수십 ms 쓴다. 그동안 DB 커넥션을 쥐고 있으면 CPU가 바쁠 때 커넥션 점유 시간이 함께 늘어
// 풀이 먼저 마른다(부하 테스트에서 본 경로). 해싱·비교 순간에 이 요청이 커넥션과 트랜잭션을 쥐고 있지 않은지 확인한다.
// 호출이 실제로 트랜잭션 밖에서 일어나야 해서 테스트 자체에 @Transactional을 붙이지 않는다.
@SpringBootTest
class AuthServiceTransactionTest {

    private static final String LOGIN_ID = "txbcrypt";
    private static final String PASSWORD = "password1!";

    @Autowired AuthService authService;
    @Autowired DataSource dataSource;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoSpyBean PasswordEncoder passwordEncoder;

    @PersistenceContext EntityManager em;

    private final List<Snapshot> snapshots = new ArrayList<>();

    @BeforeEach
    void recordStateDuringHashing() {
        doAnswer(invocation -> {
            snapshots.add(snapshot());
            return invocation.callRealMethod();
        }).when(passwordEncoder).encode(any());
        doAnswer(invocation -> {
            snapshots.add(snapshot());
            return invocation.callRealMethod();
        }).when(passwordEncoder).matches(any(), any());
    }

    // 커밋하는 테스트라 롤백에 기댈 수 없다
    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from RefreshToken t where t.user.loginId = :loginId")
                    .setParameter("loginId", LOGIN_ID).executeUpdate();
            em.createQuery("delete from User u where u.loginId = :loginId")
                    .setParameter("loginId", LOGIN_ID).executeUpdate();
        });
    }

    @Test
    void 회원가입의_비밀번호_해싱은_커넥션과_트랜잭션_밖에서_한다() {
        authService.signup(signupRequest());

        assertThat(snapshots).singleElement().isEqualTo(Snapshot.RELEASED);
    }

    @Test
    void 로그인의_비밀번호_비교는_커넥션과_트랜잭션_밖에서_한다() {
        authService.signup(signupRequest());
        snapshots.clear();

        authService.login(new LoginRequest(LOGIN_ID, PASSWORD));

        // 첫 인증이면 DaoAuthenticationProvider가 계정 없음 대비용 더미 해시를 한 번 만들어 encode도 불린다
        assertThat(snapshots).isNotEmpty().containsOnly(Snapshot.RELEASED);
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(LOGIN_ID, PASSWORD, "트랜잭션", LocalDate.of(2000, 1, 1),
                "tx@example.com", "01012345678");
    }

    private Snapshot snapshot() throws SQLException {
        int active = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
        return new Snapshot(active, TransactionSynchronizationManager.isActualTransactionActive());
    }

    record Snapshot(int activeConnections, boolean transactionActive) {
        static final Snapshot RELEASED = new Snapshot(0, false);
    }
}
