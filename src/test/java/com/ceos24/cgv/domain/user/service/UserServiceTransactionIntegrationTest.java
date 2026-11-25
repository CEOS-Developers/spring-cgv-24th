package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.dto.request.UserRequest;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserServiceTransactionIntegrationTest {

    private static AnnotationConfigApplicationContext context;
    private static UserService service;
    private static UserRepository users;
    private static TransactionTemplate database;

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(UserService.class);
        users = context.getBean(UserRepository.class);
        database = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 회원정보수정이_반환된뒤_새트랜잭션에서도_변경한닉네임이_조회된다() throws Exception {
        String username = createAndAuthenticateUser();

        service.updateUser(new UserRequest(username, null, "변경 후"));

        assertEquals("변경 후", nicknameFromDatabase(username));
    }

    @Test
    void 기존트랜잭션에서_회원정보를수정하면_외부롤백시_변경도_롤백된다() {
        String username = createAndAuthenticateUser();

        database.executeWithoutResult(status -> {
            assertDoesNotThrow(() -> service.updateUser(new UserRequest(username, null, "변경 후")));
            status.setRollbackOnly();
        });

        assertEquals("변경 전", nicknameFromDatabase(username));
    }

    private String createAndAuthenticateUser() {
        String username = "tx-user-" + UUID.randomUUID();
        service.addUser(new UserRequest(username, "password", "변경 전"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
        return username;
    }

    private String nicknameFromDatabase(String username) {
        return database.execute(status -> users.findByUsernameAndIsLockAndIsSocial(username, false, false)
                .orElseThrow().getNickname());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory() {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(new DriverManagerDataSource(
                    "jdbc:h2:mem:user-service-transaction;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setPackagesToScan("com.ceos24.cgv.domain.user.entity");
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        UserRepository users(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
                    .getRepository(UserRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        UserService userService(UserRepository users, PasswordEncoder passwordEncoder) {
            return new UserService(users, passwordEncoder);
        }
    }
}
