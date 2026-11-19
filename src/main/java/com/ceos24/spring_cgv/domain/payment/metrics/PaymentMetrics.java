package com.ceos24.spring_cgv.domain.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

// 결제 도메인의 비즈니스 메트릭을 한곳에서 기록한다.
// 태그에는 종류가 고정된 값(result)만 쓴다. orderId, screeningId 같은 값은 로그(MDC)로 찾는다.
@Component
public class PaymentMetrics {

    private static final String SEAT_HOLD = "seat.hold";

    private final Map<SeatHoldResult, Counter> seatHoldCounters = new EnumMap<>(SeatHoldResult.class);

    public PaymentMetrics(MeterRegistry registry) {

        // 미리 등록해 두면 아직 한 번도 발생하지 않은 결과도 0으로 노출되어 비율 계산이 끊기지 않는다.
        for (SeatHoldResult result : SeatHoldResult.values()) {
            seatHoldCounters.put(result, Counter.builder(SEAT_HOLD)
                    .description("좌석 선점 시도 결과")
                    .tag("result", result.getTag())
                    .register(registry));
        }
    }

    /***
     * 함수 기능: 좌석 선점 시도 결과를 기록한다.
     * @param result 선점 결과
     */
    public void seatHold(SeatHoldResult result) {
        seatHoldCounters.get(result).increment();
    }
}
