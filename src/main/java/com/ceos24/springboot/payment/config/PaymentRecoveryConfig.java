package com.ceos24.springboot.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PaymentRecoveryConfig {
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("purchase-expiration-");
        scheduler.setPoolSize(1);
        return scheduler;
    }

    // PG 장애가 일반 예매/매점 만료 스케줄러까지 지연시키지 않도록 전용 스레드를 사용한다.
    @Bean
    public ThreadPoolTaskScheduler paymentRecoveryTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("payment-recovery-");
        scheduler.setPoolSize(1);
        return scheduler;
    }
}
