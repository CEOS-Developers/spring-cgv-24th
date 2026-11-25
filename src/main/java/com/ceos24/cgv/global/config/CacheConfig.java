package com.ceos24.cgv.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

// 캐시 설정(만료·크기)은 application.yaml의 spring.cache에 둔다. 여기서는 켜고 이름만 모은다.
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String MOVIES = "movies";
}
