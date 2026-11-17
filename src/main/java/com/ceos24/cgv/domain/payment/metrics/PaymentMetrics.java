package com.ceos24.cgv.domain.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// 결과별 카운터를 미리 등록해 한 번도 발생하지 않은 결과도 0으로 노출한다(대시보드·알림에서 시계열 부재와 0을 구분하지 않도록)
@Component
public class PaymentMetrics {

	public enum ApprovalResult { APPROVED, REJECTED, UNKNOWN, COMPENSATED, COMPENSATION_FAILED }

	public enum CancelResult { DONE, REJECTED, UNKNOWN }

	private final Map<ApprovalResult, Counter> approvals = new EnumMap<>(ApprovalResult.class);
	private final Map<CancelResult, Counter> cancels = new EnumMap<>(CancelResult.class);
	private final AtomicInteger approvalRecoveryBacklog = new AtomicInteger();
	private final AtomicInteger cancelRecoveryBacklog = new AtomicInteger();

	public PaymentMetrics(MeterRegistry meterRegistry) {
		for (ApprovalResult result : ApprovalResult.values()) {
			approvals.put(result, Counter.builder("payment.approvals").description("결제 승인 처리 결과")
					.tag("result", tag(result)).register(meterRegistry));
		}
		for (CancelResult result : CancelResult.values()) {
			cancels.put(result, Counter.builder("payment.cancels").description("결제 취소 처리 결과")
					.tag("result", tag(result)).register(meterRegistry));
		}
		Gauge.builder("payment.recovery.backlog", approvalRecoveryBacklog, AtomicInteger::get)
				.description("직전 자동 복구 실행에서 정리하지 못한 건수").tag("type", "approval").register(meterRegistry);
		Gauge.builder("payment.recovery.backlog", cancelRecoveryBacklog, AtomicInteger::get)
				.description("직전 자동 복구 실행에서 정리하지 못한 건수").tag("type", "cancel").register(meterRegistry);
	}

	public void recordApproval(ApprovalResult result) {
		approvals.get(result).increment();
	}

	public void recordCancel(CancelResult result) {
		cancels.get(result).increment();
	}

	public void updateApprovalRecoveryBacklog(int count) {
		approvalRecoveryBacklog.set(count);
	}

	public void updateCancelRecoveryBacklog(int count) {
		cancelRecoveryBacklog.set(count);
	}

	private static String tag(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}
}
