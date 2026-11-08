package com.ceos24.cgv.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 결제위젯 연동 키만 받는다. API 개별 연동 키(test_ck_/test_sk_)와 섞으면 토스가 INVALID_API_KEY로 거절하고,
 * 과제 범위가 테스트 결제뿐이라 라이브 키(live_)도 막는다. 잘못 넣으면 결제 시점이 아니라 기동 시점에 실패한다.
 *
 * @param clientKey 브라우저 SDK 초기화용. 노출돼도 된다.
 * @param secretKey 서버 전용. 이 키로 승인·취소를 할 수 있으므로 Git과 클라이언트 코드에 두지 않는다.
 */
@Validated
@ConfigurationProperties("toss-payments")
public record TossPaymentsProperties(
        @NotBlank String baseUrl,
        @NotBlank @Pattern(regexp = "^test_gck_.+") String clientKey,
        @NotBlank @Pattern(regexp = "^test_gsk_.+") String secretKey
) {
}
