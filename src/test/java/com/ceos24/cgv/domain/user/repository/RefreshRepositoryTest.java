package com.ceos24.cgv.domain.user.repository;

import com.ceos24.cgv.domain.user.entity.RefreshEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class RefreshRepositoryTest {

    @Test
    void 해시와_만료시각으로_조회하고_토큰삭제와_만료정리를_실행한다() {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource("jdbc:h2:mem:refresh-repository;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("com.ceos24.cgv.domain.user.entity");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        try (EntityManager em = Objects.requireNonNull(factory.getObject()).createEntityManager()) {
            em.getTransaction().begin();
            try {
                var repository = new JpaRepositoryFactory(em).getRepository(RefreshRepository.class);
                LocalDateTime now = LocalDateTime.now();
                persist(em, "active-user", "a".repeat(64), now.plusDays(1), now);
                persist(em, "expired-user", "b".repeat(64), now.minusSeconds(1), now);
                em.flush();
                em.clear();

                assertTrue(repository.existsByTokenHashAndExpiresAtAfter("a".repeat(64), now));
                assertFalse(repository.existsByTokenHashAndExpiresAtAfter("b".repeat(64), now));
                repository.deleteByExpiresAtBefore(now);
                em.flush();
                assertEquals(1, repository.count());
                repository.deleteByTokenHash("a".repeat(64));
                em.flush();
                assertEquals(0, repository.count());

                persist(em, "another-user", "c".repeat(64), now.plusDays(1), now);
                em.flush();
                repository.deleteByUsername("another-user");
                em.flush();
                assertEquals(0, repository.count());
                em.getTransaction().commit();
            } catch (RuntimeException | Error exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw exception;
            }
        } finally {
            factory.destroy();
        }
    }

    private void persist(EntityManager em, String username, String hash, LocalDateTime expiry, LocalDateTime createdAt) {
        RefreshEntity entity = RefreshEntity.create(username, hash, expiry);
        // 이 저장소 단독 테스트에는 Spring JPA auditing 컨텍스트가 없다.
        ReflectionTestUtils.setField(entity, "createdDate", createdAt);
        em.persist(entity);
    }
}
