package com.ceos24.cgv.domain.payment.scheduler;

import com.ceos24.cgv.domain.payment.service.PaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

	private final PaymentFacade paymentFacade;

	@Scheduled(fixedDelay = 60000)
	public void recoverStalePayments() {
		paymentFacade.recoverStalePayments();
	}

	@Scheduled(fixedDelay = 60000)
	public void recoverStaleCancels() {
		paymentFacade.recoverStaleCancels();
	}
}
