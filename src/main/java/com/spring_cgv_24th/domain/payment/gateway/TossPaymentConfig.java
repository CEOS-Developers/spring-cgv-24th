package com.spring_cgv_24th.domain.payment.gateway;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TossPaymentConfig {

    @Bean
    public PaymentGateway paymentGateway(@Value("${toss.secret-key}") String secretKey) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestClient client = RestClient.builder().baseUrl("https://api.tosspayments.com")
                .requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(secretKey, "", StandardCharsets.UTF_8))
                .build();
        return new TossPaymentGateway(client);
    }
}
