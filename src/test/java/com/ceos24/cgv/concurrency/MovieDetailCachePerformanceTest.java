package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.support.SqlCountInspector;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 같은 영화 ID를 순차로 반복 조회해 적중률이 높게 나오도록 설계한 실험이다 (캐시 적용 전후 같은 코드로 측정)
class MovieDetailCachePerformanceTest extends MovieCacheTestSupport {

    private static final int MOVIE_COUNT = 100;
    private static final int WARMUP_ROUNDS = 2;
    private static final int WARM_RUNS = 200;

    @Test
    void 같은_영화_상세를_순차로_반복_조회해_SQL_횟수와_응답_시간을_측정한다() {
        List<Long> movieIds = new ArrayList<>();
        for (int i = 1; i <= MOVIE_COUNT; i++) {
            movieIds.add(saveMovieWithDetails(i));
        }
        Long targetId = movieIds.getLast();

        // JVM·DB 워밍업: 측정 대상이 아닌 영화 99편을 2회씩 조회 (캐시 유무와 관계없이 DB 조회 경로를 같은 횟수만큼 실행)
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            movieIds.subList(0, MOVIE_COUNT - 1).forEach(movieService::findById);
            clearCache();
        }
        CacheStats statsBefore = cacheStats();

        SqlCountInspector.clear();
        long coldStart = System.nanoTime();
        movieService.findById(targetId);
        long coldNanos = System.nanoTime() - coldStart;
        int coldSql = SqlCountInspector.total();

        SqlCountInspector.clear();
        List<Long> warmNanos = new ArrayList<>();
        for (int i = 0; i < WARM_RUNS; i++) {
            long start = System.nanoTime();
            movieService.findById(targetId);
            warmNanos.add(System.nanoTime() - start);
        }
        int warmSql = SqlCountInspector.total();
        CacheStats statsAfter = cacheStats();

        List<Long> sorted = warmNanos.stream().sorted().toList();
        double avgMs = warmNanos.stream().mapToLong(Long::longValue).average().orElseThrow() / 1e6;
        double medianMs = sorted.get(WARM_RUNS / 2) / 1e6;
        double p95Ms = sorted.get((int) Math.ceil(WARM_RUNS * 0.95) - 1) / 1e6;
        String cacheResult = "cache=OFF";
        if (statsAfter != null) {
            CacheStats window = statsAfter.minus(statsBefore);
            cacheResult = String.format("cache=ON hits=%d misses=%d hitRate=%.4f",
                    window.hitCount(), window.missCount(), window.hitRate());
        }
        System.out.printf("[MOVIE-DETAIL] %s coldMs=%.3f coldSql=%d warmRuns=%d warmSql=%d "
                        + "warmAvgMs=%.4f warmMedianMs=%.4f warmP95Ms=%.4f warmMinMs=%.4f warmMaxMs=%.4f%n",
                cacheResult, coldNanos / 1e6, coldSql, WARM_RUNS, warmSql,
                avgMs, medianMs, p95Ms, sorted.getFirst() / 1e6, sorted.getLast() / 1e6);

        assertThat(coldSql).isEqualTo(4);
    }
}
