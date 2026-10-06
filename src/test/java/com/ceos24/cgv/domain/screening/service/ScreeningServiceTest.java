package com.ceos24.cgv.domain.screening.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.screening.dto.ScreeningInfo;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
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
class ScreeningServiceTest {

    @Mock
    private ScreeningRepository screeningRepository;

    @InjectMocks
    private ScreeningService screeningService;

    @Test
    @DisplayName("상영 정보를 성공적으로 조회한다")
    void getScreeningInfo_Success() {
        // given
        Long screeningId = 1L;
        Movie movie = new Movie("테스트 영화");
        ReflectionTestUtils.setField(movie, "id", 1L);
        Screen screen = new Screen(null, null, "1관", 100L);
        ReflectionTestUtils.setField(screen, "id", 1L);
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusHours(2);

        Screening screening = new Screening(movie, screen, startTime, endTime);
        ReflectionTestUtils.setField(screening, "id", screeningId);

        given(screeningRepository.findById(screeningId)).willReturn(Optional.of(screening));

        // when
        ScreeningInfo result = screeningService.getScreeningInfo(screeningId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.movieInfo().title()).isEqualTo("테스트 영화");
        assertThat(result.screenInfo().name()).isEqualTo("1관");
        assertThat(result.startTime()).isEqualTo(startTime);
        assertThat(result.endTime()).isEqualTo(endTime);
    }

    @Test
    @DisplayName("상영 정보 조회 시 존재하지 않는 상영일정이면 예외가 발생한다")
    void getScreeningInfo_NotFound() {
        // given
        Long screeningId = 999L;
        given(screeningRepository.findById(screeningId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> screeningService.getScreeningInfo(screeningId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SCREENING_NOT_FOUND.getMessage())
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCREENING_NOT_FOUND);
    }
}
