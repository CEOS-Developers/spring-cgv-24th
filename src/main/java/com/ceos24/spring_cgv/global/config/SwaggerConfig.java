package com.ceos24.spring_cgv.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI swagger(){

        Info info = new Info().title("CEOS24 CGV API").description("세오스 24기 CGV 클론코딩 API 문서").version("0.0.1");

        return new OpenAPI()
                .info(info)
                .addServersItem(new Server().url("/").description("API 서버"));
    }
}