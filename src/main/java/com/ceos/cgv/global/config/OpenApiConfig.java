package com.ceos.cgv.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "CGV Clone API",
        version = "1.0.0",
        description = "CEOS 24기 백엔드 스터디 CGV 클론 코딩 API 문서",
        contact = @Contact(
                name = "CEOS 24기 백엔드 스터디",
                url = "https://github.com/CEOS-Developers/spring-cgv-24th"
        )
))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT",
        description = "로그인 시 발급된 Access Token을 입력합니다. Refresh Token은 /api/v1/auth/refresh 본문에 사용합니다.")
public class OpenApiConfig {
    @Bean
    public OpenApiCustomizer securedOperationResponses() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().stream()
                    .filter(operation -> operation.getSecurity() != null
                            && operation.getSecurity().stream()
                            .anyMatch(requirement -> requirement.containsKey("bearerAuth")))
                    .forEach(operation -> {
                        if (operation.getResponses() == null) {
                            operation.setResponses(new ApiResponses());
                        }
                        operation.getResponses().putIfAbsent("401", new ApiResponse()
                                .description("Access Token이 없거나 유효하지 않습니다."));
                    }));
        };
    }
}
