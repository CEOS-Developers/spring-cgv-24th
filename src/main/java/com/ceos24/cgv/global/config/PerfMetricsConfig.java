package com.ceos24.cgv.global.config;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("perf")
public class PerfMetricsConfig {

    @Bean
    MeterBinder perfSqlMetrics(EntityManagerFactory entityManagerFactory) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        return registry -> FunctionCounter.builder("cgv.perf.sql.prepared", statistics,
                        Statistics::getPrepareStatementCount)
                .description("Prepared statements across this perf application; measure without other traffic")
                .register(registry);
    }
}
