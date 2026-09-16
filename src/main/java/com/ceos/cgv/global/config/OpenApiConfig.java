package com.ceos.cgv.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
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
public class OpenApiConfig {
}
