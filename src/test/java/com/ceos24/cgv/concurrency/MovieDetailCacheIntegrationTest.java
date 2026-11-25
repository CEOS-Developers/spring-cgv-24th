package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.movie.dto.request.MovieImageCreateRequest;
import com.ceos24.cgv.domain.movie.dto.request.MoviePersonCreateRequest;
import com.ceos24.cgv.domain.movie.dto.request.MovieStatisticsRequest;
import com.ceos24.cgv.domain.movie.dto.request.MovieUpdateRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieDetailResponse;
import com.ceos24.cgv.domain.movie.dto.response.PersonResponse;
import com.ceos24.cgv.domain.movie.enums.MovieImageType;
import com.ceos24.cgv.domain.movie.enums.PersonRole;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.service.MovieImageService;
import com.ceos24.cgv.domain.movie.service.MoviePersonService;
import com.ceos24.cgv.domain.movie.service.MovieStatisticsService;
import com.ceos24.cgv.global.config.CacheConfig;
import com.ceos24.cgv.global.exception.GeneralException;
import com.ceos24.cgv.support.SqlCountInspector;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovieDetailCacheIntegrationTest extends MovieCacheTestSupport {

    private static final int DETAIL_SQL_COUNT = 4;

    @Autowired
    private MovieImageService movieImageService;
    @Autowired
    private MoviePersonService moviePersonService;
    @Autowired
    private MovieStatisticsService movieStatisticsService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void 첫_조회는_Miss로_DB를_조회하고_두번째_조회는_Hit로_SQL_없이_같은_응답을_반환한다() {
        Long movieId = saveMovieWithDetails(1);
        CacheStats before = cacheStats();

        SqlCountInspector.clear();
        MovieDetailResponse first = movieService.findById(movieId);
        assertThat(SqlCountInspector.total()).isEqualTo(DETAIL_SQL_COUNT);

        SqlCountInspector.clear();
        MovieDetailResponse second = movieService.findById(movieId);
        assertThat(SqlCountInspector.total()).isZero();

        CacheStats window = cacheStats().minus(before);
        assertThat(window.missCount()).isEqualTo(1);
        assertThat(window.hitCount()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
        assertThat(first.movieImageUrls()).hasSize(1 + STILL_COUNT);
        assertThat(first.persons()).hasSize(1 + ACTOR_COUNT);
    }

    @Test
    void 영화_상세_캐시는_TTL_60초_최대_1000개로_설정된다() {
        Policy<Object, Object> policy = nativeCache().policy();

        assertThat(policy.expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.eviction().orElseThrow().getMaximum()).isEqualTo(1_000L);
    }

    // 60초를 기다리지 않도록 이 테스트에서만 실행 중인 캐시의 TTL을 1초로 줄였다가 되돌린다
    @Test
    void TTL이_지나면_캐시가_만료되어_다시_DB를_조회한다() throws InterruptedException {
        Long movieId = saveMovieWithDetails(1);
        Policy.FixedExpiration<Object, Object> expiration = nativeCache().policy().expireAfterWrite().orElseThrow();
        expiration.setExpiresAfter(Duration.ofSeconds(1));
        try {
            movieService.findById(movieId);
            SqlCountInspector.clear();
            movieService.findById(movieId);
            assertThat(SqlCountInspector.total()).isZero();
            CacheStats before = cacheStats();

            Thread.sleep(1_500);
            SqlCountInspector.clear();
            long start = System.nanoTime();
            movieService.findById(movieId);
            long elapsedNanos = System.nanoTime() - start;

            CacheStats window = cacheStats().minus(before);
            System.out.printf("[MOVIE-DETAIL-TTL] afterExpiryMs=%.3f sql=%d misses=%d hits=%d%n",
                    elapsedNanos / 1e6, SqlCountInspector.total(), window.missCount(), window.hitCount());
            assertThat(SqlCountInspector.total()).isEqualTo(DETAIL_SQL_COUNT);
            assertThat(window.missCount()).isEqualTo(1);
        } finally {
            expiration.setExpiresAfter(CacheConfig.MOVIE_DETAILS_TTL);
        }
    }

    @Test
    void 캐시에_저장되는_응답의_컬렉션은_수정할_수_없다() {
        Long movieId = saveMovieWithDetails(1);

        MovieDetailResponse response = movieService.findById(movieId);

        assertThatThrownBy(() -> response.movieImageUrls().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> response.persons().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 영화_ID마다_캐시가_따로_관리되고_수정한_영화의_캐시만_무효화된다() {
        Long movieA = saveMovieWithDetails(1);
        Long movieB = saveMovieWithDetails(2);
        movieService.findById(movieA);
        movieService.findById(movieB);

        movieService.update(movieA, titleUpdate("A 수정"));

        assertThat(movieDetailsCache().get(movieA)).isNull();
        SqlCountInspector.clear();
        assertThat(movieService.findById(movieB).title()).isEqualTo("영화 2");
        assertThat(SqlCountInspector.total()).isZero();
        assertThat(movieService.findById(movieA).title()).isEqualTo("A 수정");
        assertThat(SqlCountInspector.total()).isEqualTo(DETAIL_SQL_COUNT);
    }

    @Test
    void 영화를_수정하면_다음_조회에서_최신_정보를_반환한다() {
        Long movieId = saveMovieWithDetails(1);
        movieService.findById(movieId);

        movieService.update(movieId, titleUpdate("수정된 제목"));

        assertThat(movieService.findById(movieId).title()).isEqualTo("수정된 제목");
    }

    @Test
    void 이미지를_추가하면_해당_영화의_캐시가_무효화된다() {
        Long movieId = saveMovieWithDetails(1);
        movieService.findById(movieId);

        movieImageService.create(movieId, new MovieImageCreateRequest("new-still", MovieImageType.STILL));

        assertThat(movieDetailsCache().get(movieId)).isNull();
        assertThat(movieService.findById(movieId).movieImageUrls()).contains("new-still").hasSize(2 + STILL_COUNT);
    }

    @Test
    void 인물을_추가하면_해당_영화의_캐시가_무효화된다() {
        Long movieId = saveMovieWithDetails(1);
        movieService.findById(movieId);

        moviePersonService.create(movieId, new MoviePersonCreateRequest("새 배우", null, PersonRole.ACTOR));

        assertThat(movieDetailsCache().get(movieId)).isNull();
        assertThat(movieService.findById(movieId).persons()).extracting(PersonResponse::name).contains("새 배우");
    }

    @Test
    void 통계를_수정하면_해당_영화의_캐시가_무효화된다() {
        Long movieId = saveMovieWithDetails(1);
        movieService.findById(movieId);

        movieStatisticsService.createOrUpdate(movieId, new MovieStatisticsRequest(777_777, 33.3, 99.0, null));

        assertThat(movieDetailsCache().get(movieId)).isNull();
        MovieDetailResponse response = movieService.findById(movieId);
        assertThat(response.audienceCount()).isEqualTo(777_777);
        assertThat(response.reservationRate()).isEqualTo(33.3);
        assertThat(response.eggScore()).isEqualTo(99.0);
    }

    @Test
    void 영화를_삭제하면_캐시된_응답을_반환하지_않는다() {
        Long movieId = saveMovieWithDetails(1);
        movieService.findById(movieId);

        movieService.delete(movieId);

        assertThatThrownBy(() -> movieService.findById(movieId))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.MOVIE_NOT_FOUND);
    }

    @Test
    void 캐시_무효화는_바깥_트랜잭션이_커밋된_뒤에_실행된다() {
        Long movieId = saveMovieWithDetails(1);
        movieService.findById(movieId);

        transactionTemplate.executeWithoutResult(status -> {
            movieService.update(movieId, titleUpdate("커밋 후 반영"));
            assertThat(movieDetailsCache().get(movieId)).isNotNull();
        });

        assertThat(movieDetailsCache().get(movieId)).isNull();
        assertThat(movieService.findById(movieId).title()).isEqualTo("커밋 후 반영");
    }

    @Test
    void 바깥_트랜잭션이_롤백되면_캐시를_무효화하지_않는다() {
        Long movieId = saveMovieWithDetails(1);
        movieService.findById(movieId);

        transactionTemplate.executeWithoutResult(status -> {
            movieService.update(movieId, titleUpdate("롤백될 제목"));
            status.setRollbackOnly();
        });

        assertThat(movieDetailsCache().get(movieId)).isNotNull();
        SqlCountInspector.clear();
        assertThat(movieService.findById(movieId).title()).isEqualTo("영화 1");
        assertThat(SqlCountInspector.total()).isZero();
        assertThat(movieRepository.findById(movieId).orElseThrow().getTitle()).isEqualTo("영화 1");
    }

    @Test
    void 롤백된_트랜잭션에서_조회한_미커밋_데이터는_캐시에_저장되지_않는다() {
        Long movieId = saveMovieWithDetails(1);

        transactionTemplate.executeWithoutResult(status -> {
            movieService.update(movieId, titleUpdate("커밋되지 않은 제목"));
            assertThat(movieService.findById(movieId).title()).isEqualTo("커밋되지 않은 제목");
            status.setRollbackOnly();
        });

        assertThat(movieDetailsCache().get(movieId)).isNull();
        assertThat(movieService.findById(movieId).title()).isEqualTo("영화 1");
    }

    @Test
    void 수정이_예외로_실패하면_캐시를_무효화하지_않는다() {
        Long movieId = saveMovieWithDetails(1);
        movieService.findById(movieId);

        assertThatThrownBy(() -> movieService.update(movieId, new MovieUpdateRequest(
                "실패할 제목", null, null, null, null, null, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1))))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.INVALID_SCREENING_PERIOD);

        assertThat(movieDetailsCache().get(movieId)).isNotNull();
        assertThat(movieService.findById(movieId).title()).isEqualTo("영화 1");
    }

    private MovieUpdateRequest titleUpdate(String title) {
        return new MovieUpdateRequest(title, null, null, null, null, null, null, null);
    }
}
