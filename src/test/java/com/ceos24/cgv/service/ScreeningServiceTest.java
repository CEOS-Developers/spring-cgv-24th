package com.ceos24.cgv.service;

import com.ceos24.cgv.domain.*;
import com.ceos24.cgv.dto.response.ScreeningResponse;
import com.ceos24.cgv.dto.response.ScreeningSeatsResponse;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.repository.ReservationSeatRepository;
import com.ceos24.cgv.repository.ReservationSeatRepository.SeatCountProjection;
import com.ceos24.cgv.repository.ReservationSeatRepository.SeatPositionProjection;
import com.ceos24.cgv.repository.ScreeningRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScreeningServiceTest {

    @Mock ScreeningRepository screeningRepository;
    @Mock ReservationSeatRepository reservationSeatRepository;
    @InjectMocks ScreeningService service;

    // ─── search ──────────────────────────────────────────────────────────────

    @Test
    void 회차_없으면_빈_리스트_반환() {
        given(screeningRepository.searchWithGraph(any(), any(), any(), any())).willReturn(List.of());

        List<ScreeningResponse> result = service.search(null, null, null);

        assertThat(result).isEmpty();
        // 회차가 없으면 예약 좌석 카운트 쿼리를 불필요하게 날리지 않아야 한다
        verify(reservationSeatRepository, never()).countGroupedByScreeningIds(any());
    }

    @Test
    void 회차_있고_예약_없으면_남은좌석이_전체좌석수() {
        given(screeningRepository.searchWithGraph(any(), any(), any(), any()))
                .willReturn(List.of(screening8x10With(1L, 14000)));
        given(reservationSeatRepository.countGroupedByScreeningIds(List.of(1L))).willReturn(List.of());

        List<ScreeningResponse> result = service.search(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).remainingSeats()).isEqualTo(80); // 8×10, 예약 0
    }

    @Test
    void 회차_여러개_중_일부만_예약있을때_남은좌석수_정확() {
        Screening s1 = screening8x10With(1L, 14000);
        Screening s2 = screening8x10With(2L, 14000);
        given(screeningRepository.searchWithGraph(any(), any(), any(), any())).willReturn(List.of(s1, s2));

        SeatCountProjection proj = mock(SeatCountProjection.class);
        given(proj.getScreeningId()).willReturn(1L);
        given(proj.getReservedCount()).willReturn(3L);
        given(reservationSeatRepository.countGroupedByScreeningIds(List.of(1L, 2L))).willReturn(List.of(proj));

        List<ScreeningResponse> result = service.search(null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).remainingSeats()).isEqualTo(77); // 80 - 3
        assertThat(result.get(1).remainingSeats()).isEqualTo(80); // map에 키 없으면 0L 기본값
    }

    @Test
    void date_지정하면_해당_날짜_범위로_검색() {
        LocalDate date = LocalDate.of(2024, 6, 1);
        given(screeningRepository.searchWithGraph(any(), any(), any(), any())).willReturn(List.of());

        service.search(null, null, date);

        then(screeningRepository).should().searchWithGraph(
                null,
                null,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        );
    }

    // ─── getSeats ─────────────────────────────────────────────────────────────

    @Test
    void 없는_회차면_SCREENING_NOT_FOUND() {
        given(screeningRepository.findByIdWithTheaterType(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSeats(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCREENING_NOT_FOUND);
    }

    @Test
    void 예약된_좌석이_없으면_빈_라벨_리스트() {
        given(screeningRepository.findByIdWithTheaterType(1L))
                .willReturn(Optional.of(screening8x10With(1L, 14000)));
        given(reservationSeatRepository.findPositionsByScreeningId(1L)).willReturn(List.of());

        ScreeningSeatsResponse response = service.getSeats(1L);

        assertThat(response.reservedSeats()).isEmpty();
        assertThat(response.rowCount()).isEqualTo(8);
        assertThat(response.colCount()).isEqualTo(10);
    }

    @Test
    void 좌석_라벨_변환_정확성() {
        given(screeningRepository.findByIdWithTheaterType(1L))
                .willReturn(Optional.of(screening8x10With(1L, 14000)));

        SeatPositionProjection p1 = mock(SeatPositionProjection.class);
        given(p1.getRowNum()).willReturn(1);
        given(p1.getColNum()).willReturn(5);  // → "A5"

        SeatPositionProjection p2 = mock(SeatPositionProjection.class);
        given(p2.getRowNum()).willReturn(3);
        given(p2.getColNum()).willReturn(12); // → "C12"

        given(reservationSeatRepository.findPositionsByScreeningId(1L)).willReturn(List.of(p1, p2));

        ScreeningSeatsResponse response = service.getSeats(1L);

        assertThat(response.reservedSeats()).containsExactly("A5", "C12");
    }

    // ─── 픽스처 헬퍼 ─────────────────────────────────────────────────────────

    private Screening screening8x10With(long id, int price) {
        Branch branch = Branch.builder().name("강남점").address("서울 강남구").build();
        TheaterType theaterType = TheaterType.builder().name("일반").rowCount(8).colCount(10).build();
        Theater theater = Theater.builder().branch(branch).theaterType(theaterType).name("1관").build();
        Movie movie = Movie.builder()
                .title("범죄도시4").director("감독").genre("액션")
                .runningTime(120).releaseDate(LocalDate.of(2024, 1, 1)).ageRating("15세").build();
        LocalDateTime start = LocalDateTime.of(2024, 6, 1, 10, 0);
        Screening screening = Screening.builder()
                .theater(theater).movie(movie)
                .startAt(start).endAt(start.plusMinutes(120)).price(price)
                .build();
        ReflectionTestUtils.setField(screening, "id", id);
        return screening;
    }
}
