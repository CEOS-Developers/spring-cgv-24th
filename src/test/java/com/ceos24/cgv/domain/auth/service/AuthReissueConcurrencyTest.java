package com.ceos24.cgv.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ceos24.cgv.domain.auth.dto.request.LoginRequest;
import com.ceos24.cgv.domain.auth.dto.request.SignUpRequest;
import com.ceos24.cgv.domain.auth.dto.response.TokenResponse;
import com.ceos24.cgv.domain.auth.entity.RefreshToken;
import com.ceos24.cgv.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthReissueConcurrencyTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String originalRefreshToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        // 회원가입 및 로그인
        SignUpRequest signUpRequest = new SignUpRequest("홍길동", "hong@example.com", "hong123", "password123!", "user");
        authService.signUp(signUpRequest);

        LoginRequest loginRequest = new LoginRequest("hong123", "password123!");
        TokenResponse loginResponse = authService.login(loginRequest);
        originalRefreshToken = loginResponse.refreshToken();
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("동시 재발급 요청 시 유예 기간(Grace Period)에 의해 오탐지 없이 모두 성공하고 동일한 신규 토큰을 반환한다")
    void reissueConcurrency_GracePeriod_Success() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        List<TokenResponse> responses = Collections.synchronizedList(new ArrayList<>());

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    TokenResponse response = authService.reissue(originalRefreshToken);
                    responses.add(response);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isZero();
        assertThat(responses).hasSize(threadCount);

        // 10개의 요청 모두 완전히 동일한 단 하나의 신규 AccessToken과 RefreshToken을 반환받아야 함 (멱등성 검증)
        String firstAccessToken = responses.get(0).accessToken();
        String firstRefreshToken = responses.get(0).refreshToken();

        for (TokenResponse response : responses) {
            assertThat(response.accessToken()).isEqualTo(firstAccessToken);
            assertThat(response.refreshToken()).isEqualTo(firstRefreshToken);
        }
    }

    @Test
    @DisplayName("유예 기간(10초)이 경과한 후 과거 토큰 재사용 시 AUTH_HIJACK_DETECTED 예외가 발생하고 회원의 모든 토큰이 삭제된다")
    void reissue_AfterGracePeriod_ThrowsHijackDetected() throws Exception {
        // given
        // 1회 정상 재발급 수행
        TokenResponse reissuedResponse = authService.reissue(originalRefreshToken);
        assertThat(reissuedResponse).isNotNull();

        // DB에 저장된 originalRefreshToken 엔티티의 usedAt 시간을 11초 전으로 조작 (유예 기간 10초 만료 시뮬레이션)
        RefreshToken storedToken =
                refreshTokenRepository.findByToken(originalRefreshToken).orElseThrow();
        Field usedAtField = RefreshToken.class.getDeclaredField("usedAt");
        usedAtField.setAccessible(true);
        usedAtField.set(storedToken, LocalDateTime.now().minusSeconds(11));
        refreshTokenRepository.saveAndFlush(storedToken);

        // when & then
        // 유예 기간이 지난 과거 토큰으로 다시 재발급 요청 시 탈취로 감지되어 예외 발생
        assertThatThrownBy(() -> authService.reissue(originalRefreshToken))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_HIJACK_DETECTED);

        // 해당 회원의 모든 RefreshToken이 삭제되었는지 확인 (새로 발급되었던 토큰도 삭제됨)
        assertThat(refreshTokenRepository.findByToken(reissuedResponse.refreshToken()))
                .isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }
}
