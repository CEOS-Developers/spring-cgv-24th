package com.ceos24.cgv.integration;

import com.ceos24.cgv.domain.movie.dto.request.MovieCreateRequest;
import com.ceos24.cgv.domain.movie.dto.request.MovieImageCreateRequest;
import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieImageType;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MovieImageRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.movie.service.MovieImageService;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.schedule.repository.ScheduleRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.repository.ScreenTypeRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MovieDeleteIntegrationTest {

    @Autowired
    private MovieService movieService;
    @Autowired
    private MovieImageService movieImageService;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private MovieImageRepository movieImageRepository;
    @Autowired
    private TheaterRepository theaterRepository;
    @Autowired
    private ScreenTypeRepository screenTypeRepository;
    @Autowired
    private ScreenRepository screenRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;

    @AfterEach
    void cleanUp() {
        scheduleRepository.deleteAllInBatch();
        screenRepository.deleteAllInBatch();
        screenTypeRepository.deleteAllInBatch();
        theaterRepository.deleteAllInBatch();
        movieImageRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
    }

    private Long createMovie() {
        return movieService.create(new MovieCreateRequest("영화", "드라마", 120, "설명", AgeRating.FIFTEEN,
                MovieStatus.SHOWING, LocalDate.of(2026, 9, 1), null));
    }

    @Test
    void 영화_등록_후_포스터를_등록하고_영화를_삭제한다() {
        Long movieId = createMovie();
        movieImageService.create(movieId, new MovieImageCreateRequest("poster-url", MovieImageType.POSTER));

        movieService.delete(movieId);

        assertThat(movieRepository.existsById(movieId)).isFalse();
        assertThat(movieImageRepository.findByMovieId(movieId)).isEmpty();
    }

    @Test
    void 상영_일정이_있는_영화는_삭제할_수_없다() {
        Long movieId = createMovie();
        Theater theater = theaterRepository.save(Theater.create("CGV 강남", "서울", "강남구", "설명", null));
        ScreenType screenType = screenTypeRepository.save(ScreenType.create("일반관", 2, 3));
        Screen screen = screenRepository.save(Screen.create(theater, screenType, "1관"));
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        scheduleRepository.save(Schedule.create(movieRepository.getReferenceById(movieId), screen, startTime,
                startTime.plusHours(2), 15_000));

        assertThatThrownBy(() -> movieService.delete(movieId))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.MOVIE_HAS_HISTORY);
        assertThat(movieRepository.existsById(movieId)).isTrue();
    }
}
