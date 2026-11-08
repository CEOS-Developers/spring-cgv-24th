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

    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "jwtAuth";

        // 1. API 요청을 보낼 때 'jwtAuth'라는 보안 스키마를 요구하도록 설정
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        // 2. 'jwtAuth'라는 보안 스키마가 어떤 방식인지(HTTP Bearer) 컴포넌트에 등록
        Components components = new Components()
                .addSecuritySchemes(
                        jwtSchemeName,
                        new SecurityScheme()
                                .name(jwtSchemeName)
                                .type(SecurityScheme.Type.HTTP) // HTTP 방식
                                .scheme("bearer") // Bearer 접두사 사용
                                .bearerFormat("JWT")); // 포맷은 JWT

        // 3. 위에서 만든 요구사항과 컴포넌트를 OpenAPI 객체에 담아서 반환
        return new OpenAPI()
                .info(new Info()
                        .title("CGV 클론 프로젝트 API")
                        .description("CEOS 24기 CGV 클론 API 명세서입니다.")
                        .version("1.0.0"))
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}
