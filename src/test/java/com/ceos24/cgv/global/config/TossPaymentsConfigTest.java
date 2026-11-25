package com.ceos24.cgv.global.config;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TossPaymentsConfigTest {

    @Test
    void 토스_호출은_URI_템플릿_단위로_http_client_requests에_기록된다() {
        // 정적 builder()로 되돌리면 대시보드의 토스 지연·상태 패널이 조용히 비게 된다
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        // 닿지 않는 주소라 토스를 실제로 부르지 않는다. 연결 실패도 호출 한 번으로 기록돼야 한다
        TossPaymentsProperties properties =
                new TossPaymentsProperties("http://127.0.0.1:1", "test_gck_dummy", "test_gsk_dummy");
        RestClient restClient = new TossPaymentsConfig().tossPaymentsRestClient(properties, observationRegistry);

        assertThatThrownBy(() -> restClient.get().uri("/v1/payments/{paymentKey}", "tgen_secret_key").retrieve()
                .toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);

        // paymentKey가 태그 값으로 들어가면 결제마다 시계열이 생긴다. 템플릿으로 묶여야 한다
        Timer timer = meterRegistry.find("http.client.requests").tag("uri", "/v1/payments/{paymentKey}").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
