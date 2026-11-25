package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.config.MovieCacheConfig;
import com.ceos.cgv.domain.movie.dto.MovieCreateRequest;
import com.ceos.cgv.domain.movie.dto.MovieResponse;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.global.exception.BusinessException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MovieListCacheIntegrationTest {

    private static final String TITLE_PREFIX = "캐시 테스트 영화 ";
    private static final String LIST_KEY = "list";

    @Autowired
    private MovieService movieService;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Statistics statistics;
    private Cache cache;

    @BeforeEach
    void 캐시와_통계를_초기화한다() {
        cache = cacheManager.getCache(MovieCacheConfig.PUBLIC_MOVIES);
        cache.clear();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @AfterEach
    void 테스트_영화를_정리한다() {
        jdbcTemplate.update("DELETE FROM movies WHERE title LIKE ?", TITLE_PREFIX + "%");
        cache.clear();
    }

    @Test
    void 공개_영화_목록을_다시_조회하면_캐시에서_반환하고_DB를_사용하지_않는다() {
        List<MovieResponse> first = movieService.findAllPublic();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getSessionOpenCount()).isEqualTo(1);
        long statements = statistics.getPrepareStatementCount();
        long sessions = statistics.getSessionOpenCount();
        long transactions = statistics.getTransactionCount();

        List<MovieResponse> second = movieService.findAllPublic();

        assertThat(second).isSameAs(first);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(statements);
        assertThat(statistics.getSessionOpenCount()).isEqualTo(sessions);
        assertThat(statistics.getTransactionCount()).isEqualTo(transactions);
    }

    @Test
    void 캐시에는_엔티티가_아닌_변경할_수_없는_DTO_목록이_저장된다() {
        movieService.create(request("DTO"));

        List<MovieResponse> cached = movieService.findAllPublic();

        assertThat(cache.get(LIST_KEY, List.class)).isSameAs(cached);
        assertThat(cached).isNotEmpty().allSatisfy(item -> assertThat(item).isInstanceOf(MovieResponse.class));
        assertThatThrownBy(() -> cached.add(cached.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 영화를_생성하면_캐시가_비워져_다음_조회에_새_영화가_포함된다() {
        List<MovieResponse> before = movieService.findAllPublic();
        assertThat(cache.get(LIST_KEY)).isNotNull();

        Movie created = movieService.create(request("생성"));

        assertThat(cache.get(LIST_KEY)).isNull();
        statistics.clear();
        List<MovieResponse> after = movieService.findAllPublic();

        assertThat(after).hasSize(before.size() + 1);
        assertThat(after).extracting(MovieResponse::movieId).contains(created.getId());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void 영화를_비공개_처리하면_캐시가_비워져_다음_조회에서_제외된다() {
        Movie created = movieService.create(request("비공개"));
        assertThat(movieService.findAllPublic()).extracting(MovieResponse::movieId).contains(created.getId());
        assertThat(cache.get(LIST_KEY)).isNotNull();

        movieService.delete(created.getId());

        assertThat(cache.get(LIST_KEY)).isNull();
        assertThat(movieService.findAllPublic()).extracting(MovieResponse::movieId)
                .doesNotContain(created.getId());
    }

    @Test
    void 없는_영화의_비공개_처리가_실패하면_캐시를_비우지_않는다() {
        List<MovieResponse> cached = movieService.findAllPublic();

        assertThatThrownBy(() -> movieService.delete(Long.MAX_VALUE)).isInstanceOf(BusinessException.class);

        assertThat(cache.get(LIST_KEY, List.class)).isSameAs(cached);
    }

    @Test
    void 바깥_트랜잭션이_커밋되기_전에는_캐시를_비우지_않고_커밋_뒤에_비운다() {
        Movie created = movieService.create(request("커밋"));
        List<MovieResponse> stale = movieService.findAllPublic();
        assertThat(stale).extracting(MovieResponse::movieId).contains(created.getId());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            movieService.delete(created.getId());

            // 메서드가 반환돼도 커밋 전이므로 다른 요청이 보는 캐시는 그대로이다
            assertThat(cache.get(LIST_KEY, List.class)).isSameAs(stale);
        });

        assertThat(cache.get(LIST_KEY)).isNull();
        assertThat(movieService.findAllPublic()).extracting(MovieResponse::movieId)
                .doesNotContain(created.getId());
    }

    @Test
    void 바깥_트랜잭션이_롤백되면_캐시를_비우지_않고_DB_변경도_남지_않는다() {
        Movie created = movieService.create(request("롤백"));
        List<MovieResponse> cached = movieService.findAllPublic();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            movieService.delete(created.getId());
            movieService.create(request("롤백 신규"));
            status.setRollbackOnly();
        });

        assertThat(cache.get(LIST_KEY, List.class)).isSameAs(cached);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT visibility FROM movies WHERE movie_id = ?", String.class, created.getId()))
                .isEqualTo("PUBLIC");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movies WHERE title = ?", Integer.class, TITLE_PREFIX + "롤백 신규"))
                .isZero();
        assertThat(movieService.findAllPublic()).isSameAs(cached);
    }

    private static MovieCreateRequest request(String name) {
        return new MovieCreateRequest(
                TITLE_PREFIX + name,
                "캐시 무효화 테스트용 영화",
                100,
                LocalDate.of(2026, 11, 1),
                AgeRating.ALL
        );
    }
}
