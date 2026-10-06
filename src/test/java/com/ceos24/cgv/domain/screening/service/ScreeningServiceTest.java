package com.ceos24.cgv.domain.screening.service;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository.SeatCountProjection;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository.SeatPositionProjection;
import com.ceos24.cgv.domain.screening.dto.ScreeningListResponse;
import com.ceos24.cgv.domain.screening.dto.ScreeningListResponse.BranchGroup;
import com.ceos24.cgv.domain.screening.dto.ScreeningListResponse.FormatGroup;
import com.ceos24.cgv.domain.screening.dto.ScreeningResponse;
import com.ceos24.cgv.domain.screening.dto.ScreeningSeatsResponse;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.entity.TimeSlot;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScreeningServiceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 9, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final ReservationStatus PENDING = ReservationStatus.PENDING;

    @Mock ScreeningRepository screeningRepository;
    @Mock ReservationSeatRepository reservationSeatRepository;

    ScreeningService service;

    @BeforeEach
    void setUp() {
        service = serviceAt(NOW);
    }

    // ─── 조회 범위 ────────────────────────────────────────────────────────────

    @Test
    void 날짜를_생략하면_오늘을_조회한다() {
        givenNoScreenings();

        ScreeningListResponse response = service.search(null, null, null, null, null);

        assertThat(response.date()).isEqualTo(TODAY);
        // 오늘이면 이미 시작한 회차는 고를 수 없으므로 현재 시각부터 본다
        thenSearchedBetween(NOW, TODAY.plusDays(1).atStartOfDay());
    }

    @Test
    void 미래_날짜는_그날_자정부터_조회한다() {
        givenNoScreenings();
        LocalDate tomorrow = TODAY.plusDays(1);

        service.search(null, null, tomorrow, null, null);

        thenSearchedBetween(tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay());
    }

    @Test
    void 지난_날짜는_쿼리도_날리지_않고_빈_결과() {
        ScreeningListResponse response = service.search(null, null, TODAY.minusDays(1), null, null);

        assertThat(response.branches()).isEmpty();
        verify(screeningRepository, never()).search(any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    void 시간대를_주면_그_구간으로_좁힌다() {
        givenNoScreenings();

        service.search(null, null, TODAY, null, TimeSlot.EVENING);

        thenSearchedBetween(TODAY.atTime(18, 0), TODAY.atTime(23, 0));
    }

    @Test
    void 이미_지나간_시간대를_고르면_빈_결과() {
        // 13시에 오전(0~12시) 탭을 고르면 남는 구간이 없다
        ScreeningListResponse response =
                serviceAt(TODAY.atTime(13, 0)).search(null, null, TODAY, null, TimeSlot.MORNING);

        assertThat(response.branches()).isEmpty();
        verify(screeningRepository, never()).search(any(), anyBoolean(), any(), any(), any(), any());
    }

    // ─── 필터 ─────────────────────────────────────────────────────────────────

    @Test
    void 극장을_고르지_않으면_IN_조건을_끈다() {
        givenNoScreenings();

        service.search(null, List.of(), TODAY, null, null);

        then(screeningRepository).should()
                .search(isNull(), eq(false), any(), isNull(), any(), any());
    }

    @Test
    void 극장을_여러_개_고르면_그대로_넘긴다() {
        givenNoScreenings();

        service.search(1L, List.of(7L, 9L), TODAY, TheaterType.IMAX, null);

        then(screeningRepository).should()
                .search(eq(1L), eq(true), eq(List.of(7L, 9L)), eq(TheaterType.IMAX), any(), any());
    }

    // ─── 그룹핑 ───────────────────────────────────────────────────────────────

    @Test
    void 지점_안에서_상영관_종류로_묶인다() {
        Branch gangnam = branchWith(1L, "강남");
        givenScreenings(
                screeningOf(10L, gangnam, TheaterType.IMAX, "1관", TODAY.atTime(10, 0)),
                screeningOf(11L, gangnam, TheaterType.STANDARD, "2관", TODAY.atTime(11, 0)),
                screeningOf(12L, gangnam, TheaterType.IMAX, "1관", TODAY.atTime(14, 0)));

        List<BranchGroup> branches = service.search(null, null, TODAY, null, null).branches();

        assertThat(branches).hasSize(1);
        assertThat(branches.get(0).branchName()).isEqualTo("강남");

        List<FormatGroup> formats = branches.get(0).formats();
        // TheaterType 선언 순서 — STANDARD가 IMAX보다 앞이다
        assertThat(formats).extracting(FormatGroup::theaterType)
                .containsExactly(TheaterType.STANDARD, TheaterType.IMAX);
        assertThat(formats.get(1).screeningCount()).isEqualTo(2);
        assertThat(formats.get(1).screenings()).extracting(ScreeningResponse::id)
                .containsExactly(10L, 12L);
    }

    @Test
    void 지점은_쿼리가_준_순서를_지킨다() {
        givenScreenings(
                screeningOf(10L, branchWith(1L, "강남"), TheaterType.STANDARD, "1관", TODAY.atTime(10, 0)),
                screeningOf(20L, branchWith(2L, "홍대"), TheaterType.STANDARD, "1관", TODAY.atTime(11, 0)));

        List<BranchGroup> branches = service.search(null, null, TODAY, null, null).branches();

        assertThat(branches).extracting(BranchGroup::branchName).containsExactly("강남", "홍대");
    }

    @Test
    void 점유된_좌석만큼_잔여석이_줄고_매진이_표시된다() {
        givenScreenings(screeningOf(10L, branchWith(1L, "강남"),
                TheaterType.STANDARD, "1관", TODAY.atTime(10, 0)));
        // STANDARD 8×10 = 80석
        List<SeatCountProjection> counts = List.of(seatCountOf(10L, 80L));
        given(reservationSeatRepository.countOccupiedByScreeningIds(List.of(10L), PENDING, NOW))
                .willReturn(counts);

        ScreeningResponse card = service.search(null, null, TODAY, null, null)
                .branches().get(0).formats().get(0).screenings().get(0);

        assertThat(card.totalSeats()).isEqualTo(80);
        assertThat(card.remainingSeats()).isZero();
        assertThat(card.soldOut()).isTrue();
    }

    // ─── getSeats ─────────────────────────────────────────────────────────────

    @Test
    void 없는_회차면_SCREENING_NOT_FOUND() {
        given(screeningRepository.findByIdWithTheater(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSeats(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCREENING_NOT_FOUND);
    }

    @Test
    void 막힌_좌석이_없으면_빈_라벨_리스트() {
        given(screeningRepository.findByIdWithTheater(1L)).willReturn(Optional.of(
                screeningOf(1L, branchWith(1L, "강남"), TheaterType.STANDARD, "1관", TODAY.atTime(10, 0))));

        ScreeningSeatsResponse response = service.getSeats(1L);

        assertThat(response.reservedSeats()).isEmpty();
        assertThat(response.rowCount()).isEqualTo(8);
        assertThat(response.colCount()).isEqualTo(10);
    }

    @Test
    void 좌석_라벨_변환_정확성() {
        given(screeningRepository.findByIdWithTheater(1L)).willReturn(Optional.of(
                screeningOf(1L, branchWith(1L, "강남"), TheaterType.STANDARD, "1관", TODAY.atTime(10, 0))));
        List<SeatPositionProjection> positions = List.of(positionOf(1, 5), positionOf(3, 12));
        given(reservationSeatRepository.findOccupiedPositionsByScreeningId(1L, PENDING, NOW))
                .willReturn(positions);

        assertThat(service.getSeats(1L).reservedSeats()).containsExactly("A5", "C12");
    }

    // ─── 픽스처 헬퍼 ─────────────────────────────────────────────────────────

    private ScreeningService serviceAt(LocalDateTime now) {
        return new ScreeningService(screeningRepository, reservationSeatRepository,
                Clock.fixed(now.atZone(ZONE).toInstant(), ZONE));
    }

    private void givenNoScreenings() {
        given(screeningRepository.search(any(), anyBoolean(), any(), any(), any(), any()))
                .willReturn(List.of());
    }

    private void givenScreenings(Screening... screenings) {
        given(screeningRepository.search(any(), anyBoolean(), any(), any(), any(), any()))
                .willReturn(List.of(screenings));
    }

    private void thenSearchedBetween(LocalDateTime start, LocalDateTime end) {
        then(screeningRepository).should()
                .search(any(), anyBoolean(), any(), any(), eq(start), eq(end));
    }

    private Branch branchWith(long id, String name) {
        Branch branch = Branch.builder().name(name).address("서울 어딘가").build();
        ReflectionTestUtils.setField(branch, "id", id);
        return branch;
    }

    private Screening screeningOf(long id, Branch branch, TheaterType type,
                                  String theaterName, LocalDateTime startAt) {
        Theater theater = Theater.builder().branch(branch).theaterType(type).name(theaterName).build();
        Movie movie = Movie.builder()
                .title("범죄도시4").director("감독").genre("액션")
                .runningTime(120).releaseDate(LocalDate.of(2024, 1, 1)).ageRating("15세").build();
        ReflectionTestUtils.setField(movie, "id", 1L);
        Screening screening = Screening.builder()
                .theater(theater).movie(movie)
                .startAt(startAt).endAt(startAt.plusMinutes(120)).price(14000)
                .build();
        ReflectionTestUtils.setField(screening, "id", id);
        return screening;
    }

    private SeatCountProjection seatCountOf(long screeningId, long count) {
        SeatCountProjection projection = mock(SeatCountProjection.class);
        given(projection.getScreeningId()).willReturn(screeningId);
        given(projection.getReservedCount()).willReturn(count);
        return projection;
    }

    private SeatPositionProjection positionOf(int row, int col) {
        SeatPositionProjection projection = mock(SeatPositionProjection.class);
        given(projection.getRowNum()).willReturn(row);
        given(projection.getColNum()).willReturn(col);
        return projection;
    }
}
