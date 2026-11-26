package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// 결과 불명 결제는 HTTP 지표에 드러나지 않는다. 승인 요청은 정상 응답으로 끝나고, 돈 문제는 DB의 IN_PROGRESS 행으로만 남는다.
@Configuration
public class PaymentMetricsConfig {

    // 값은 DB에서 다시 센다. 이벤트마다 올리고 내리는 방식은 앱이 재시작되면 0부터 시작해 실제 남은 건수와 어긋난다.
    // 세는 일은 수집 요청과 떼어 둔다(CachedPaymentCounts).
    @Bean
    public MeterBinder paymentMetrics(PaymentRepository paymentRepository, Clock clock) {
        CachedPaymentCounts counts = new CachedPaymentCounts(paymentRepository, clock,
                task -> Thread.ofVirtual().name("payment-metrics").start(task));
        return registry -> {
            Gauge.builder("cgv.payments.in.progress", counts, CachedPaymentCounts::inProgress)
                    .description("승인 중이거나 결과를 모르는 결제 수")
                    .strongReference(true)
                    .register(registry);
            // 승인 직후의 IN_PROGRESS는 정상이다. 정리를 시작할 시각이 지났는데도 남아 있어야 스케줄러가 못 따라가는 것이다.
            Gauge.builder("cgv.payments.in.progress.overdue", counts, CachedPaymentCounts::overdue)
                    .description("정리 시작 시각이 지났는데 아직 결과를 모르는 결제 수")
                    .strongReference(true)
                    .register(registry);
        };
    }
}
