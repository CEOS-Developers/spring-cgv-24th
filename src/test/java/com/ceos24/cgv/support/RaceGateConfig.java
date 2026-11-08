package com.ceos24.cgv.support;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.repository.Repository;

// 운영 코드에 테스트용 분기를 넣지 않고 경합 구간에 게이트를 두려고, 저장소 프록시 맨 앞에 인터셉터를 붙인다.
// 게이트를 걸지 않은 메서드는 그대로 지나간다.
@TestConfiguration(proxyBeanMethods = false)
public class RaceGateConfig {

    @Bean
    static BeanPostProcessor raceGateRepositoryInterceptor() {
        MethodInterceptor afterReturn = invocation -> {
            Object result = invocation.proceed();
            RaceGate.passed(invocation.getMethod());
            return result;
        };
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof Repository<?, ?> && bean instanceof Advised advised) {
                    advised.addAdvice(0, afterReturn);
                }
                return bean;
            }
        };
    }
}
