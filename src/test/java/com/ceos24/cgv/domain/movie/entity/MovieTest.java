package com.ceos24.cgv.domain.movie.entity;

import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovieTest {

    private static final LocalDate OPEN = LocalDate.of(2026, 9, 1);
    private static final LocalDate CLOSE = LocalDate.of(2026, 10, 1);

    private Movie movie(LocalDate openDate, LocalDate closeDate) {
        return Movie.create("영화", "드라마", 120, "설명", AgeRating.FIFTEEN, MovieStatus.SHOWING, openDate, closeDate);
    }

    private void update(Movie movie, LocalDate openDate, LocalDate closeDate) {
        movie.update(null, null, null, null, null, null, openDate, closeDate);
    }

    @Test
    void 생성_시_종료일이_개봉일보다_빠르면_거절한다() {
        assertThatThrownBy(() -> movie(CLOSE, OPEN))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.INVALID_SCREENING_PERIOD);
    }

    @Test
    void 개봉일만_수정해_기존_종료일보다_늦어지면_거절한다() {
        Movie movie = movie(OPEN, CLOSE);

        assertThatThrownBy(() -> update(movie, CLOSE.plusDays(1), null))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.INVALID_SCREENING_PERIOD);
        assertThat(movie.getOpenDate()).isEqualTo(OPEN);
    }

    @Test
    void 종료일만_수정해_기존_개봉일보다_빨라지면_거절한다() {
        Movie movie = movie(OPEN, CLOSE);

        assertThatThrownBy(() -> update(movie, null, OPEN.minusDays(1)))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.INVALID_SCREENING_PERIOD);
    }

    @Test
    void 정상_기간으로_수정하면_반영된다() {
        Movie movie = movie(OPEN, CLOSE);

        update(movie, OPEN.plusDays(1), CLOSE.plusDays(1));

        assertThat(movie.getOpenDate()).isEqualTo(OPEN.plusDays(1));
        assertThat(movie.getCloseDate()).isEqualTo(CLOSE.plusDays(1));
    }

    @Test
    void 한쪽이_null이면_통과한다() {
        assertThatCode(() -> movie(OPEN, null)).doesNotThrowAnyException();
        Movie movie = movie(OPEN, null);
        assertThatCode(() -> update(movie, OPEN.plusDays(5), null)).doesNotThrowAnyException();
        Movie noOpenDate = movie(null, CLOSE);
        assertThatCode(() -> update(noOpenDate, null, CLOSE.minusDays(5))).doesNotThrowAnyException();
    }
}
