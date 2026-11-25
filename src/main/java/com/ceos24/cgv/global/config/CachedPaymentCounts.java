package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToLongFunction;

// 수집 응답이 DB 커넥션을 기다리지 않게, 건수는 따로 세 두고 수집에는 직전에 센 값을 돌려준다.
// 수집 중에 커넥션을 기다리면 풀이 바닥난 순간 수집 전체가 제한 시간을 넘겨, 그때 가장 필요한 커넥션·요청 지표까지 함께 빠진다.
// 값은 수집 한 주기만큼 늦다. 결과 불명 결제는 분 단위로 바뀌어 문제가 되지 않는다.
// @Scheduled로 세지 않는 이유: 스케줄러 스레드 하나를 토스 호출이 있는 정리 작업과 나눠 써 그 뒤에 밀리고,
// 수집이 없는 운영에서는 셀 이유도 없다. 수집이 갱신을 깨우면 운영의 조회는 지금처럼 0번이다.
@Slf4j
class CachedPaymentCounts {

    // 이보다 오래 못 셌으면 값을 비운다. 직전 값을 계속 보여 주면 DB가 계속 안 될 때도 정상처럼 보인다.
    static final Duration STALE_AFTER = Duration.ofMinutes(1);

    private final PaymentRepository paymentRepository;
    private final Clock clock;
    private final Executor executor;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Snapshot snapshot;

    CachedPaymentCounts(PaymentRepository paymentRepository, Clock clock, Executor executor) {
        this.paymentRepository = paymentRepository;
        this.clock = clock;
        this.executor = executor;
    }

    double inProgress() {
        return read(Snapshot::inProgress);
    }

    double overdue() {
        return read(Snapshot::overdue);
    }

    private double read(ToLongFunction<Snapshot> field) {
        requestRefresh();
        Snapshot current = snapshot;
        if (current == null || clock.instant().isAfter(current.countedAt().plus(STALE_AFTER))) {
            return Double.NaN;
        }
        return field.applyAsLong(current);
    }

    // 한 수집에서 게이지 두 개가 읽혀도 갱신은 한 번만 돈다. 이전 갱신이 커넥션을 기다리는 중이면 더 쌓지 않는다.
    private void requestRefresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::refresh);
        } catch (RuntimeException e) {
            // 플래그가 남으면 갱신이 영영 멈춘다. 예외는 Micrometer가 받아 이번 값만 비운다.
            refreshing.set(false);
            throw e;
        }
    }

    private void refresh() {
        try {
            LocalDateTime overdueBefore = LocalDateTime.now(clock).minus(Payment.RECOVERY_DELAY);
            long inProgress = paymentRepository.countByStatus(PaymentStatus.IN_PROGRESS);
            long overdue = paymentRepository.countByStatusAndApprovalStartedAtLessThanEqual(
                    PaymentStatus.IN_PROGRESS, overdueBefore);
            snapshot = new Snapshot(inProgress, overdue, clock.instant());
        } catch (RuntimeException e) {
            log.warn("[Metrics] 결제 건수 갱신 실패 reason={}", e.getClass().getSimpleName());
        } finally {
            refreshing.set(false);
        }
    }

    private record Snapshot(long inProgress, long overdue, Instant countedAt) {
    }
}
