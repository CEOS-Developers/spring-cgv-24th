package com.ceos24.cgv.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    // 선점 만료와 취소 기한이 현재 시각에 의존한다. 코드가 직접 now()를 부르면
    // 10분 뒤·상영 20분 전 같은 상황을 테스트에서 만들 수 없다.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
