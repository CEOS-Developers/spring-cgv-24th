package com.ceos24.springboot.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.time.LocalDateTime;

/** DB에 남는 재시도 예약. 임대 중 서버가 종료되면 nextAttemptAt 이후 다시 처리한다. */
@Getter
@Embeddable
public class PaymentRecovery {
    @Column(name = "next_recovery_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "recovery_attempts", nullable = false)
    private int attempts;

    public boolean isDue(LocalDateTime now, LocalDateTime createdAt, LocalDateTime legacyCutoff) {
        return nextAttemptAt == null ? !createdAt.isAfter(legacyCutoff) : !nextAttemptAt.isAfter(now);
    }

    public void schedule(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public void claim(LocalDateTime leaseUntil) {
        nextAttemptAt = leaseUntil;
        attempts++;
    }
}
