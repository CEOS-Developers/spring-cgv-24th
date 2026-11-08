package com.ceos24.cgv.domain.screening.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.screening.dto.request.CreateScreeningRequest;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.service.SeatService;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScreeningAdminServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private ScreenRepository screenRepository;

    @Mock
    private ScreeningRepository screeningRepository;

    @Mock
    private SeatService seatService;

    @InjectMocks
    private ScreeningAdminService screeningAdminService;

    @Test
    @DisplayName("상영 일정을 성공적으로 생성한다")
    void createScreening_Success() {
        // given
        Long movieId = 1L;
        Long screenId = 1L;
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusHours(2);
        CreateScreeningRequest request = new CreateScreeningRequest(movieId, screenId, startTime, endTime);

        Movie movie = new Movie("테스트 영화");
        ReflectionTestUtils.setField(movie, "id", movieId);

        Screen screen = new Screen(null, null, "1관", 100L);
        ReflectionTestUtils.setField(screen, "id", screenId);

        given(movieRepository.findById(movieId)).willReturn(Optional.of(movie));
        given(screenRepository.findById(screenId)).willReturn(Optional.of(screen));

        // when
        screeningAdminService.createScreening(request);

        // then
        then(screeningRepository).should(times(1)).save(any(Screening.class));
        then(seatService).should(times(1)).createSeats(any(Screening.class));
    }

    @Test
    @DisplayName("상영 일정 생성 시 존재하지 않는 영화면 예외가 발생한다")
    void createScreening_MovieNotFound() {
        // given
        Long movieId = 999L;
        Long screenId = 1L;
        CreateScreeningRequest request = new CreateScreeningRequest(
                movieId, screenId, LocalDateTime.now(), LocalDateTime.now().plusHours(2));

        given(movieRepository.findById(movieId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> screeningAdminService.createScreening(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.MOVIE_NOT_FOUND.getMessage())
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MOVIE_NOT_FOUND);

        then(screenRepository).shouldHaveNoInteractions();
        then(screeningRepository).shouldHaveNoInteractions();
        then(seatService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("상영 일정 생성 시 존재하지 않는 상영관이면 예외가 발생한다")
    void createScreening_ScreenNotFound() {
        // given
        Long movieId = 1L;
        Long screenId = 999L;
        CreateScreeningRequest request = new CreateScreeningRequest(
                movieId, screenId, LocalDateTime.now(), LocalDateTime.now().plusHours(2));

        Movie movie = new Movie("테스트 영화");
        ReflectionTestUtils.setField(movie, "id", movieId);

        given(movieRepository.findById(movieId)).willReturn(Optional.of(movie));
        given(screenRepository.findById(screenId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> screeningAdminService.createScreening(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SCREEN_NOT_FOUND.getMessage())
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCREEN_NOT_FOUND);

        then(screeningRepository).shouldHaveNoInteractions();
        then(seatService).shouldHaveNoInteractions();
    }
}
