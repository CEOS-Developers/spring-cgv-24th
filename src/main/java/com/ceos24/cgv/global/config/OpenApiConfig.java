package com.ceos24.cgv.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cgvOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CGV 클론 코딩 API")
                        .description(
                                "영화·영화관 조회, 찜, 상영정보, " +
                                        "좌석 예매·취소, 매점 구매 API"
                        )
                        .version("1.0.0"));
    }
}