package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CachedPaymentCountsTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant START = Instant.parse("2026-11-25T03:00:00Z");

    @Mock PaymentRepository paymentRepository;

    // 갱신을 바로 돌리지 않고 쌓아 둔다. 수집 시점에 DB를 부르지 않는지, 갱신이 몇 번 요청됐는지 보려는 것이다.
    private final List<Runnable> submitted = new ArrayList<>();
    private final MutableClock clock = new MutableClock(START, ZONE);
    private CachedPaymentCounts counts;

    @BeforeEach
    void setUp() {
        counts = new CachedPaymentCounts(paymentRepository, clock, submitted::add);
    }

    @Test
    void 처음_수집하면_값이_없고_DB를_기다리지_않으며_갱신은_한_번만_요청한다() {
        assertThat(counts.inProgress()).isNaN();
        assertThat(counts.overdue()).isNaN();

        assertThat(submitted).hasSize(1);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void 갱신이_끝나면_다음_수집에서_센_값을_돌려준다() {
        LocalDateTime overdueBefore = LocalDateTime.ofInstant(START, ZONE).minus(Payment.RECOVERY_DELAY);
        given(paymentRepository.countByStatus(PaymentStatus.IN_PROGRESS)).willReturn(3L);
        given(paymentRepository.countByStatusAndApprovalStartedAtLessThanEqual(PaymentStatus.IN_PROGRESS, overdueBefore))
                .willReturn(1L);

        counts.inProgress();
        runSubmitted();

        assertThat(counts.inProgress()).isEqualTo(3.0);
        assertThat(counts.overdue()).isEqualTo(1.0);
    }

    @Test
    void 갱신이_실패하면_직전_값을_유지하고_다음_수집에서_다시_센다() {
        given(paymentRepository.countByStatus(PaymentStatus.IN_PROGRESS))
                .willReturn(3L)
                .willThrow(new DataAccessResourceFailureException("Unable to acquire JDBC Connection"));
        given(paymentRepository.countByStatusAndApprovalStartedAtLessThanEqual(any(), any())).willReturn(1L);
        counts.inProgress();
        runSubmitted();

        counts.inProgress();
        runSubmitted();

        assertThat(counts.inProgress()).isEqualTo(3.0);
        assertThat(counts.overdue()).isEqualTo(1.0);
        assertThat(submitted).hasSize(1);
    }

    @Test
    void 일분_넘게_세지_못하면_값을_비운다() {
        given(paymentRepository.countByStatus(PaymentStatus.IN_PROGRESS))
                .willReturn(3L)
                .willThrow(new DataAccessResourceFailureException("Unable to acquire JDBC Connection"));
        given(paymentRepository.countByStatusAndApprovalStartedAtLessThanEqual(any(), any())).willReturn(1L);
        counts.inProgress();
        runSubmitted();

        clock.advance(CachedPaymentCounts.STALE_AFTER.plusSeconds(1));
        counts.inProgress();
        runSubmitted();

        assertThat(counts.inProgress()).isNaN();
        assertThat(counts.overdue()).isNaN();
    }

    private void runSubmitted() {
        List<Runnable> tasks = new ArrayList<>(submitted);
        submitted.clear();
        tasks.forEach(Runnable::run);
    }

    private static final class MutableClock extends Clock {

        private Instant now;
        private final ZoneId zone;

        MutableClock(Instant now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
