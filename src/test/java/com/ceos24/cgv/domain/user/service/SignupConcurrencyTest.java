package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.SignupRequest;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.MySqlContainerConfig;
import com.ceos24.cgv.support.RaceGate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 같은 아이디의 가입이 겹칠 때 계정이 하나만 생기는지 실제 MySQL에서 확인한다.
// 두 요청을 중복 확인 직후에 세워 둘 다 "없는 아이디"를 본 뒤 INSERT하게 한다. 이 틈은 유니크 제약만 막는다.
// 스레드마다 트랜잭션이 따로 열려야 해서 ControllerIntegrationTest(@Transactional)를 쓰지 않는다.
@SpringBootTest
@Import(MySqlContainerConfig.class)
class SignupConcurrencyTest {

    private static final String LOGIN_ID = "signuprace";

    @Autowired AuthService authService;
    @Autowired TransactionTemplate transactionTemplate;

    @PersistenceContext EntityManager em;

    // 커밋하는 테스트라 롤백에 기댈 수 없다. 남기면 다른 테스트의 목록 단언이 깨진다.
    @AfterEach
    void tearDown() {
        RaceGate.disarmAll();
        transactionTemplate.executeWithoutResult(status ->
                em.createQuery("delete from User u where u.loginId = :loginId")
                        .setParameter("loginId", LOGIN_ID)
                        .executeUpdate());
    }

    @Test
    void 두_요청이_모두_없는_아이디를_확인한_뒤_가입해도_계정은_하나만_생긴다() throws Exception {
        RaceGate gate = RaceGate.arm(UserRepository.class, "existsByLoginId", 2);

        List<Object> outcomes = signupConcurrently(2);

        assertThat(accountCount()).isEqualTo(1);
        assertThat(outcomes).filteredOn(o -> !(o instanceof ErrorCode)).hasSize(1);
        assertThat(outcomes).filteredOn(ErrorCode.class::isInstance)
                .singleElement().isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);
        // 둘 다 중복 확인을 통과했다. 경합이 실제로 겹쳤으므로 우연히 통과한 결과가 아니다.
        assertThat(gate.aloneCount()).isZero();
    }

    private List<Object> signupConcurrently(int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                SignupRequest request = new SignupRequest(LOGIN_ID, "password" + i + "!", "가입경합" + i,
                        LocalDate.of(2000, 1, 1), "race" + i + "@example.com", "0101234567" + i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return authService.signup(request);
                    } catch (CustomException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private long accountCount() {
        return transactionTemplate.execute(status -> em.createQuery(
                        "select count(u) from User u where u.loginId = :loginId", Long.class)
                .setParameter("loginId", LOGIN_ID)
                .getSingleResult());
    }
}
