package com.ceos24.cgv.global.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndRegisterModules();
    }

    @Bean
    public RestClient tossCommandRestClient(TossProperties tossProperties) {
        return createTossRestClient(tossProperties, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    @Bean
    public RestClient tossQueryRestClient(TossProperties tossProperties) {
        return createTossRestClient(tossProperties, Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    private RestClient createTossRestClient(
            TossProperties tossProperties, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        String encodedSecretKey =
                Base64.getEncoder().encodeToString((tossProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(tossProperties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
