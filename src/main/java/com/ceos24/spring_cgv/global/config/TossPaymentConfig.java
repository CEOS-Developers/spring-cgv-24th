package com.ceos24.spring_cgv.global.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Configuration
@Getter
public class TossPaymentConfig {

    // 토스페이먼츠에 결제 승인 요청에 보낼 URL
    public static final String URL = "https://api.tosspayments.com/v1/payments";

    // 토스 서버와 TCP 연결을 맺기까지 기다리는 시간
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    // 연결 후 응답 본문을 받기까지 기다리는 시간
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final String testClientApiKey;

    private final String testSecretKey;

    private final String successUrl;

    private final String failureUrl;

    public TossPaymentConfig(
            @Value("${payment.toss.test_client_api_key}") String testClientApiKey,
            @Value("${payment.toss.test_secret_api_key}") String testSecretKey,
            @Value("${payment.toss.success_url}") String successUrl,
            @Value("${payment.toss.failure_url}") String failureUrl) {
        this.testClientApiKey = testClientApiKey;
        this.testSecretKey = testSecretKey;
        this.successUrl = successUrl;
        this.failureUrl = failureUrl;
    }

    @Bean
    public RestClient tossRestClient(){

        String auth = Base64.getEncoder()
                .encodeToString((testSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(URL)
                .requestFactory(createRequestFactory())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /***
     * 함수 기능: 타임아웃이 설정된 요청 팩토리를 만든다.
     *          RestClient의 기본값은 무한 대기다. confirm()이 @Transactional 안에서 토스를
     *          호출하므로, 응답이 오지 않으면 트랜잭션이 열린 채 DB 커넥션을 계속 점유한다.
     * @return 연결/읽기 타임아웃이 적용된 요청 팩토리
     */
    private JdkClientHttpRequestFactory createRequestFactory() {

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build());

        requestFactory.setReadTimeout(READ_TIMEOUT);

        return requestFactory;
    }
}
