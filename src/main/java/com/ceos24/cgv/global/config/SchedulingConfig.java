package com.ceos24.cgv.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 결과 불명 결제·취소 정리가 주기적으로 돈다. 테스트에서는 끈다(scheduling.enabled=false).
// 켜 두면 테스트가 시계를 미는 사이 정리가 끼어들어 결과가 실행마다 달라진다. 테스트는 정리 메서드를 직접 부른다.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
