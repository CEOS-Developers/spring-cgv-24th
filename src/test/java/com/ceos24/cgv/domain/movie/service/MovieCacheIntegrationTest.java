package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieCreateRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieResponse;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.cache.CacheNames;
import com.ceos24.cgv.global.config.MovieCacheConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CGV_REDIS_TEST_PORT", matches = "[0-9]+")
class MovieCacheIntegrationTest {
    private static AnnotationConfigApplicationContext context;
    private static MovieService service;
    private static MovieRepository movies;
    private static TransactionTemplate database;
    private static Statistics statistics;
    private static Cache cache;
    private static LettuceConnectionFactory redis;
    private static String keyPrefix;

    @BeforeAll
    static void start() {
        keyPrefix = "cgv:test:" + UUID.randomUUID() + ":";
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("movie-cache-test", Map.of(
                "spring.cache.type", "redis", "cgv.cache.key-prefix", keyPrefix,
                "cgv.cache.movie-list-ttl-seconds", "30")));
        context.register(Config.class);
        context.refresh();
        service = context.getBean(MovieService.class);
        movies = context.getBean(MovieRepository.class);
        database = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
        cache = Objects.requireNonNull(context.getBean(CacheManager.class).getCache(CacheNames.MOVIE_LIST));
        redis = context.getBean(LettuceConnectionFactory.class);
    }

    @AfterAll
    static void stop() {
        try {
            if (cache != null) cache.evict("all");
        } finally {
            if (context != null) context.close();
        }
    }

    @BeforeEach
    void resetPrivateDataset() {
        cache.evict("all");
        database.executeWithoutResult(status -> movies.deleteAll());
        statistics.clear();
    }

    @Test
    void 첫조회는_DB를읽고_캐시적중시_DTO와날짜를복원하며_SQL을실행하지않는다() {
        Long id = createMovie("캐시 영화");
        long before = statistics.getPrepareStatementCount();
        List<MovieResponse> first = service.getMovies();
        assertEquals(1L, statistics.getPrepareStatementCount() - before);

        long beforeHit = statistics.getPrepareStatementCount();
        List<MovieResponse> hit = service.getMovies();

        assertEquals(List.of(new MovieResponse(id, "캐시 영화", LocalDate.of(2026, 9, 15))), hit);
        assertEquals(first, hit);
        assertEquals(0L, statistics.getPrepareStatementCount() - beforeHit);
    }

    @Test
    void 빈영화목록도_캐싱한다() {
        assertEquals(List.of(), service.getMovies());
        long before = statistics.getPrepareStatementCount();

        assertEquals(List.of(), service.getMovies());
        assertEquals(0L, statistics.getPrepareStatementCount() - before);
    }

    @Test
    void 영화생성은_외부트랜잭션_커밋후_캐시를삭제한다() {
        createMovie("기존 영화");
        List<MovieResponse> previous = service.getMovies();

        database.executeWithoutResult(status -> {
            createMovie("새 영화");
            assertEquals(previous, Objects.requireNonNull(cache.get("all")).get());
        });

        assertNull(cache.get("all"));
        assertEquals(List.of("기존 영화", "새 영화"), service.getMovies().stream().map(MovieResponse::name).toList());
    }

    @Test
    void 영화삭제_커밋후_다음조회에_삭제결과가반영된다() {
        Long id = createMovie("삭제할 영화");
        service.getMovies();

        service.deleteMovie(id);

        assertNull(cache.get("all"));
        assertEquals(List.of(), service.getMovies());
    }

    @Test
    void 외부트랜잭션이_롤백되면_DB와캐시가_기존상태를유지한다() {
        createMovie("기존 영화");
        List<MovieResponse> previous = service.getMovies();

        database.executeWithoutResult(status -> {
            createMovie("롤백할 영화");
            status.setRollbackOnly();
        });

        assertEquals(previous, Objects.requireNonNull(cache.get("all")).get());
        long count = Objects.requireNonNull(database.execute(status -> movies.count()));
        assertEquals(1L, count);
    }

    @Test
    void 메서드반환후_커밋이실패해도_캐시를삭제하지않는다() {
        Long id = createMovie("기존 영화");
        List<MovieResponse> previous = service.getMovies();

        assertThrows(IllegalStateException.class, () -> database.executeWithoutResult(status -> {
            service.deleteMovie(id);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("simulated commit failure");
                }
            });
        }));

        assertEquals(previous, Objects.requireNonNull(cache.get("all")).get());
        long count = Objects.requireNonNull(database.execute(status -> movies.count()));
        assertEquals(1L, count);
    }

    @Test
    void Redis키에_TTL이설정되고_만료되면_DB를다시조회한다() throws Exception {
        createMovie("만료 테스트");
        List<MovieResponse> previous = service.getMovies();
        byte[] key = (keyPrefix + CacheNames.MOVIE_LIST + "::all").getBytes(StandardCharsets.UTF_8);
        try (var connection = redis.getConnection()) {
            Long ttl = connection.keyCommands().pTtl(key);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= 30_000);
            connection.keyCommands().pExpire(key, 1);
        }
        Thread.sleep(20);

        long before = statistics.getPrepareStatementCount();
        assertEquals(previous, service.getMovies());
        assertEquals(1L, statistics.getPrepareStatementCount() - before);
    }

    private Long createMovie(String name) {
        return service.createMovie(new MovieCreateRequest(name, LocalDate.of(2026, 9, 15)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import(MovieCacheConfig.class)
    static class Config {
        @Bean
        LettuceConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",
                    Integer.parseInt(System.getenv("CGV_REDIS_TEST_PORT"))));
        }

        @Bean
        ObjectMapper objectMapper() { return JsonMapper.builder().build(); }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory() {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(new DriverManagerDataSource(
                    "jdbc:h2:mem:movie-cache;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
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
        MovieRepository movies(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(MovieRepository.class); }

        @Bean
        MovieService movieService(MovieRepository movies) { return new MovieService(movies); }
    }
}
