package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentOperation;
import com.ceos24.cgv.domain.payment.fixture.PaymentTestFixtures;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRepositoryTest {

    private static LocalContainerEntityManagerFactoryBean factoryBean;
    private static EntityManagerFactory factory;
    private static TransactionTemplate transaction;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:payment-models;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.setPackagesToScan("com.ceos24.cgv.domain");
        factoryBean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factoryBean.afterPropertiesSet();
        factory = Objects.requireNonNull(factoryBean.getObject());
        transaction = new TransactionTemplate(new JpaTransactionManager(factory));
    }

    @AfterAll
    static void closeDatabase() {
        if (factoryBean != null) {
            factoryBean.destroy();
        }
    }

    @Test
    void DB가_주문당_두개의_결제생성을_막는다() {
        PersistenceException exception = assertThrows(PersistenceException.class, () ->
                transaction.executeWithoutResult(status -> {
                    EntityManager em = entityManager();
                    Payment payment = persistPayment(em);
                    em.persist(Payment.create(payment.getPurchase()));
                    em.flush();
                }));
        // OneToOne 매핑의 자동 UNIQUE 이름은 Hibernate/DB마다 달라질 수 있다.
        var violation = assertInstanceOf(org.hibernate.exception.ConstraintViolationException.class, exception);
        assertEquals("23505", violation.getSQLState());
    }

    @Test
    void DB가_다른_주문에_같은_PG결제키를_저장하지_못하게_한다() {
        String paymentKey = UUID.randomUUID().toString();
        PersistenceException exception = assertThrows(PersistenceException.class, () ->
                transaction.executeWithoutResult(status -> {
                    EntityManager em = entityManager();
                    Payment first = persistPayment(em);
                    first.approve(paymentKey, "카드", Instant.now());
                    em.flush();
                    Payment second = persistPayment(em);
                    second.approve(paymentKey, "카드", Instant.now());
                    em.flush();
                }));
        assertTrue(exception.getMessage().toLowerCase().contains("uk_payment_key"));
    }

    @Test
    void DB가_동일_결제_작업종류_멱등키의_중복을_막는다() {
        PersistenceException exception = assertThrows(PersistenceException.class, () ->
                transaction.executeWithoutResult(status -> {
                    EntityManager em = entityManager();
                    Payment payment = persistPayment(em);
                    em.persist(PaymentOperation.startConfirm(payment, "attempt-1", "same-request"));
                    em.persist(PaymentOperation.startConfirm(payment, "attempt-2", "same-request"));
                    em.flush();
                }));
        assertTrue(exception.getMessage().toLowerCase().contains("uk_payment_operation_request"));
    }

    @Test
    void DB가_다른_작업에서_PG용_멱등키를_재사용하지_못하게_한다() {
        PersistenceException exception = assertThrows(PersistenceException.class, () ->
                transaction.executeWithoutResult(status -> {
                    EntityManager em = entityManager();
                    PaymentOperation first = PaymentOperation.startConfirm(persistPayment(em), "attempt-1", "request");
                    PaymentOperation second = PaymentOperation.startConfirm(persistPayment(em), "attempt-2", "request");
                    ReflectionTestUtils.setField(second, "pgIdempotencyKey", first.getPgIdempotencyKey());
                    em.persist(first);
                    em.persist(second);
                    em.flush();
                }));
        assertTrue(exception.getMessage().toLowerCase().contains("uk_payment_operation_pg_key"));
    }

    @Test
    void 오래된_결제버전으로_다른_승인결과를_덮어쓰지_못한다() {
        Long paymentId = transaction.execute(status -> persistPayment(entityManager()).getId());
        EntityManager first = factory.createEntityManager();
        EntityManager second = factory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Payment firstCopy = first.find(Payment.class, paymentId);
            Payment secondCopy = second.find(Payment.class, paymentId);
            firstCopy.approve("first-" + paymentId, "카드", Instant.now());
            secondCopy.approve("second-" + paymentId, "카드", Instant.now());

            first.getTransaction().commit();
            assertThrows(RollbackException.class, () -> second.getTransaction().commit());

            transaction.executeWithoutResult(status -> assertEquals("first-" + paymentId,
                    entityManager().find(Payment.class, paymentId).getPaymentKey()));
        } finally {
            if (first.getTransaction().isActive()) {
                first.getTransaction().rollback();
            }
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            first.close();
            second.close();
        }
    }

    @Test
    void DB가_한_영화예매에_중복_결제를_만들지못하게한다() {
        PersistenceException exception = assertThrows(PersistenceException.class, () -> transaction.executeWithoutResult(status -> {
            EntityManager em = entityManager();
            Payment store = persistPayment(em);
            Reservation reservation = PaymentTestFixtures.persistReservation(em, store.getPayer(), store.getPurchase().getCinema());
            em.persist(Payment.createForReservation(reservation, 30_000, LocalDateTime.now().plusMinutes(10)));
            em.persist(Payment.createForReservation(reservation, 30_000, LocalDateTime.now().plusMinutes(10)));
            em.flush();
        }));
        assertEquals("23505", assertInstanceOf(org.hibernate.exception.ConstraintViolationException.class, exception).getSQLState());
    }

    @Test
    void DB가_매점과_예매에서_같은_주문번호를_사용하지못하게한다() {
        PersistenceException exception = assertThrows(PersistenceException.class, () -> transaction.executeWithoutResult(status -> {
            EntityManager em = entityManager();
            Payment store = persistPayment(em);
            Reservation reservation = PaymentTestFixtures.persistReservation(em, store.getPayer(), store.getPurchase().getCinema());
            Payment movie = Payment.createForReservation(reservation, 30_000, LocalDateTime.now().plusMinutes(10));
            ReflectionTestUtils.setField(movie, "orderId", store.getOrderId());
            em.persist(movie);
            em.flush();
        }));
        assertTrue(exception.getMessage().toLowerCase().contains("uk_payment_order"));
    }

    @Test
    void DB가_결제대상이_둘이거나_없거나_종류가다른_연결을_거절한다() {
        for (String mutation : List.of("reservation_id = :reservationId", "purchase_id = null", "target_type = 'RESERVATION'")) {
            PersistenceException exception = assertThrows(PersistenceException.class, () -> transaction.executeWithoutResult(status -> {
                EntityManager em = entityManager();
                Payment store = persistPayment(em);
                Reservation reservation = PaymentTestFixtures.persistReservation(em, store.getPayer(), store.getPurchase().getCinema());
                em.flush();
                var update = em.createNativeQuery("update payment set " + mutation + " where id = :id")
                        .setParameter("id", store.getId());
                if (mutation.contains(":reservationId")) update.setParameter("reservationId", reservation.getId());
                update.executeUpdate();
            }));
            assertEquals("23513", assertInstanceOf(org.hibernate.exception.ConstraintViolationException.class, exception).getSQLState());
        }
    }

    private EntityManager entityManager() {
        return Objects.requireNonNull(EntityManagerFactoryUtils.getTransactionalEntityManager(factory));
    }

    private Payment persistPayment(EntityManager em) {
        UserEntity user = UserEntity.createLocalUser("payment-" + UUID.randomUUID(), "encoded", "결제 사용자");
        Cinema cinema = Cinema.create("결제 테스트 영화관", "주소", "서울");
        em.persist(user);
        em.persist(cinema);
        Purchase purchase = Purchase.create(user, cinema, 10_000);
        em.persist(purchase);
        Payment payment = Payment.create(purchase);
        em.persist(payment);
        em.flush();
        return payment;
    }
}
