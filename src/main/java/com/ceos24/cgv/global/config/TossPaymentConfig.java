package com.ceos24.cgv.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class TossPaymentConfig {

	@Bean
	public RestClient tossRestClient(@Value("${toss.secret-key}") String secretKey) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(3));
		requestFactory.setReadTimeout(Duration.ofSeconds(30));

		return RestClient.builder()
				.baseUrl("https://api.tosspayments.com")
				.requestFactory(requestFactory)
				.defaultHeaders(headers -> headers.setBasicAuth(secretKey, "")) // "시크릿키:"를 base64 인코딩
				.build();
	}
}
