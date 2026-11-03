package com.ceos24.cgv.global.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

//Toss API를 호출시에 사용할 HTTP 통신 객체를 만들어서 Spring에 등록하는 클래스
@Configuration
public class TossConfig {

    //주소와 인증정보를 설정한 RestClient를 만들어서 등록하기
    @Bean
    public RestClient tossRestClient(

            //서버 주소
            @Value("${toss.base-url}") String baseUrl,
            //시크릿 키
            @Value("${toss.secret-key}") String secretKey
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(secretKey, ""))
                .build();
    }
}