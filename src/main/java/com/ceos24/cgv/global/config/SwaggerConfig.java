package com.ceos24.cgv.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("CGV 클론코딩 API")
                .description("CEOS 24기 CGV 클론코딩 프로젝트 API 명세서")
                .version("v1.0.0");

        return new OpenAPI()
                .info(info);
    }
}
