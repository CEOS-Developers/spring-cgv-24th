package com.ceos24.cgv.domain.screening.service;

import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumType;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumTypeCategory;
import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.repository.AuditoriumRepository;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.cinema.repository.SeatRepository;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.reservation.service.ReservationHoldService;
import com.ceos24.cgv.domain.screening.dto.response.ScreeningResponse;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ScreeningServiceQueryIntegrationTest {

    private static AnnotationConfigApplicationContext context;
    private static ScreeningService service;
    private static EntityManager em;
    private static TransactionTemplate database;
    private static Statistics statistics;

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(ScreeningService.class);
        em = context.getBean(EntityManager.class);
        database = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void 회차수가늘어도_두SQL로_해당영화관의_정확한응답을_시간순으로_조회한다(int count) {
        Fixture fixture = fixture(count, true);
        long before = statistics.getPrepareStatementCount();

        List<ScreeningResponse> actual = service.getScreeningsByCinema(fixture.cinemaId());

        assertEquals(fixture.expected(), actual);
        assertEquals(2L, statistics.getPrepareStatementCount() - before,
                "영화관 존재 확인 1회와 연관 데이터를 포함한 상영정보 조회 1회");
    }

    @Test
    void 활성영화관에_상영정보가없으면_빈목록을반환한다() {
        Long cinemaId = database.execute(status -> {
            Cinema cinema = Cinema.create("빈 영화관", "주소", "서울");
            em.persist(cinema);
            return cinema.getId();
        });

        assertEquals(List.of(), service.getScreeningsByCinema(cinemaId));
    }

    @Test
    void 존재하지않는영화관은_기존오류를반환한다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getScreeningsByCinema(Long.MAX_VALUE));

        assertEquals(ErrorCode.CINEMA_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void 비활성영화관은_상영정보가있어도_기존오류를반환한다() {
        Fixture fixture = fixture(1, false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getScreeningsByCinema(fixture.cinemaId()));

        assertEquals(ErrorCode.CINEMA_NOT_FOUND, exception.getErrorCode());
    }

    private Fixture fixture(int count, boolean active) {
        return database.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Cinema cinema = Cinema.create("영화관 " + suffix, "주소", "서울");
            if (!active) cinema.deactivate();
            em.persist(cinema);
            AuditoriumType type = AuditoriumType.create("일반관 " + suffix,
                    AuditoriumTypeCategory.NORMAL, 10, 10);
            em.persist(type);

            List<ScreeningResponse> expected = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Movie movie = Movie.create("영화 " + i, LocalDate.of(2026, 1, 1));
                Auditorium auditorium = Auditorium.create(cinema, type, "상영관 " + i);
                em.persist(movie);
                em.persist(auditorium);
                // Deliberately insert in reverse time order and use different associated entities.
                LocalDateTime startsAt = LocalDateTime.of(2030, 1, 1, 9, 0).plusHours(count - i);
                Screening screening = Screening.create(auditorium, movie, startsAt, startsAt.plusHours(2), 12000 + i);
                em.persist(screening);
                expected.add(ScreeningResponse.from(screening));
            }

            Cinema otherCinema = Cinema.create("다른 영화관 " + suffix, "다른 주소", "부산");
            Movie otherMovie = Movie.create("제외할 영화", LocalDate.of(2026, 1, 1));
            em.persist(otherCinema);
            em.persist(otherMovie);
            Auditorium otherAuditorium = Auditorium.create(otherCinema, type, "다른 상영관");
            em.persist(otherAuditorium);
            LocalDateTime otherStartsAt = LocalDateTime.of(2030, 1, 1, 8, 0);
            em.persist(Screening.create(otherAuditorium, otherMovie, otherStartsAt, otherStartsAt.plusHours(2), 9000));

            expected.sort(Comparator.comparing(ScreeningResponse::startsAt));
            return new Fixture(cinema.getId(), List.copyOf(expected));
        });
    }

    private record Fixture(Long cinemaId, List<ScreeningResponse> expected) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory() {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(new DriverManagerDataSource(
                    "jdbc:h2:mem:screening-query;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setPackagesToScan("com.ceos24.cgv.domain");
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.generate_statistics", "true"));
            return factory;
        }

        @Bean
        @Primary
        EntityManager entityManager(EntityManagerFactory factory) {
            return SharedEntityManagerCreator.createSharedEntityManager(factory);
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        ScreeningRepository screenings(EntityManager em) {
            return new JpaRepositoryFactory(em).getRepository(ScreeningRepository.class);
        }

        @Bean
        CinemaRepository cinemas(EntityManager em) {
            return new JpaRepositoryFactory(em).getRepository(CinemaRepository.class);
        }

        @Bean
        ScreeningService screeningService(ScreeningRepository screenings, CinemaRepository cinemas) {
            return new ScreeningService(screenings, mock(AuditoriumRepository.class), mock(MovieRepository.class),
                    cinemas, mock(SeatRepository.class), mock(ReservationSeatRepository.class),
                    mock(ReservationHoldService.class));
        }
    }
}
