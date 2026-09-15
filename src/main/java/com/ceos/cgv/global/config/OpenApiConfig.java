package com.ceos.cgv.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "CGV Clone API",
        version = "v1",
        description = "영화 조회, 예매, 찜과 매점 주문 API"
))
public class OpenApiConfig {
}
