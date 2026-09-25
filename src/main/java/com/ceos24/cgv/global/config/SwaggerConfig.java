package com.ceos24.cgv.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CGV Clone API")
                        .description("CEOS 24기 스프링 튜토리얼 2주차")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // 전역으로 걸어 Authorize에 한 번 넣은 토큰이 모든 요청에 실리게 한다.
                // 공개 API에도 실리지만 필터가 공개 경로를 막지 않으므로 무해하다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
