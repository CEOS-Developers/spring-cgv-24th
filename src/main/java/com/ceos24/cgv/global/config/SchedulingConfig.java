package com.ceos24.cgv.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {
}
