package com.ceos.cgv.domain.payment.demo;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration(proxyBeanMethods = false)
@Profile("payment-demo")
public class PaymentDemoDatabaseGuard {
    @Bean
    static BeanPostProcessor paymentDemoDataSourceGuard() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource) {
                    requireInMemoryH2(dataSource);
                }
                return bean;
            }
        };
    }

    static void requireInMemoryH2(DataSource dataSource) {
        // DataSource 초기화 직후 검사하여 JPA의 create-drop이 외부 DB에 닿지 않게 한다.
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            if (url == null || !url.startsWith("jdbc:h2:mem:")) {
                throw new IllegalStateException(
                        "The payment-demo profile requires an isolated H2 in-memory database");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("The payment-demo database could not be verified");
        }
    }
}
