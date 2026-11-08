package com.ceos24.cgv.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class TossPaymentsConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // 토스는 응답이 최대 60초 걸릴 수 있다며 최소 60초를 권한다. 짧게 잡으면 토스에서는 승인됐는데
    // 우리는 실패로 처리하는 불일치가 생긴다.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    public RestClient tossPaymentsRestClient(TossPaymentsProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthorization(properties.secretKey()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // Basic 인증의 "아이디:비밀번호"에서 시크릿 키가 아이디, 비밀번호는 빈 값이다.
    // 콜론을 빼면 키가 맞아도 401(UNAUTHORIZED_KEY)이 난다.
    private static String basicAuthorization(String secretKey) {
        String credentials = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
