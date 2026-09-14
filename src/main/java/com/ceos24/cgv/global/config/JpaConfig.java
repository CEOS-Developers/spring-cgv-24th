package com.ceos24.cgv.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @SpringBootApplication이 아닌 별도 클래스에 두어
// @WebMvcTest 등 부분 컨텍스트 테스트에서 JPA 의존이 끌려오지 않게 한다.
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
