package com.ceos24.cgv.service;

import com.ceos24.cgv.domain.*;
import com.ceos24.cgv.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.dto.response.ReservationResponse;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.repository.ReservationRepository;
import com.ceos24.cgv.repository.ReservationSeatRepository;
import com.ceos24.cgv.repository.ScreeningRepository;
import com.ceos24.cgv.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock ScreeningRepository screeningRepository;
    @Mock UserRepository userRepository;
    @Mock ReservationSeatRepository reservationSeatRepository;
    @InjectMocks ReservationService service;

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    void 없는_회차면_SCREENING_NOT_FOUND() {
        given(screeningRepository.findByIdWithTheaterType(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(reqOf(1L, 1L, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCREENING_NOT_FOUND);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void 없는_사용자면_MEMBER_NOT_FOUND() {
        given(screeningRepository.findByIdWithTheaterType(1L)).willReturn(Optional.of(screening8x10With(1L, 14000)));
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(reqOf(1L, 99L, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 좌석이_범위를_벗어나면_SEAT_OUT_OF_RANGE() {
        given(screeningRepository.findByIdWithTheaterType(1L)).willReturn(Optional.of(screening8x10With(1L, 14000)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L)));

        // rowCount=8 인 상영관에 row=9 요청
        assertThatThrownBy(() -> service.create(reqOf(1L, 1L, new int[]{9, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SEAT_OUT_OF_RANGE);
        verify(reservationSeatRepository, never()).findByScreeningId(any());
    }

    @Test
    void 요청_내_좌석이_중복이면_DUPLICATE_SEAT_IN_REQUEST() {
        given(screeningRepository.findByIdWithTheaterType(1L)).willReturn(Optional.of(screening8x10With(1L, 14000)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L)));

        assertThatThrownBy(() -> service.create(reqOf(1L, 1L, new int[]{1, 1}, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
        verify(reservationSeatRepository, never()).findByScreeningId(any());
    }

    @Test
    void 이미_예매된_좌석이면_SEAT_ALREADY_RESERVED_pre_check() {
        given(screeningRepository.findByIdWithTheaterType(1L)).willReturn(Optional.of(screening8x10With(1L, 14000)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L)));
        given(reservationSeatRepository.findByScreeningId(1L)).willReturn(List.of(seatOf(1, 1)));

        assertThatThrownBy(() -> service.create(reqOf(1L, 1L, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_요청_유니크제약_충돌시_SEAT_ALREADY_RESERVED() {
        given(screeningRepository.findByIdWithTheaterType(1L)).willReturn(Optional.of(screening8x10With(1L, 14000)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L)));
        given(reservationSeatRepository.findByScreeningId(1L)).willReturn(List.of());
        given(reservationRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uk_seat_screening_row_col"));

        assertThatThrownBy(() -> service.create(reqOf(1L, 1L, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);
    }

    @Test
    void 정상_예매_생성() {
        given(screeningRepository.findByIdWithTheaterType(1L)).willReturn(Optional.of(screening8x10With(1L, 14000)));
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithId(2L)));
        given(reservationSeatRepository.findByScreeningId(1L)).willReturn(List.of());
        given(reservationRepository.saveAndFlush(any())).willAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 10L);
            return r;
        });

        // 정렬 검증을 위해 순서를 뒤섞어 요청 (B3 → A2 → A1)
        ReservationResponse response = service.create(reqOf(1L, 2L, new int[]{2, 3}, new int[]{1, 2}, new int[]{1, 1}));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(response.seats()).containsExactly("A1", "A2", "B3"); // rowNum/colNum 기준 정렬 확인
        assertThat(response.totalPrice()).isEqualTo(14000 * 3);
    }

    @Test
    void 예매_시_screening_현재가격이_각_좌석에_스냅샷된다() {
        given(screeningRepository.findByIdWithTheaterType(1L)).willReturn(Optional.of(screening8x10With(1L, 14000)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L)));
        given(reservationSeatRepository.findByScreeningId(1L)).willReturn(List.of());
        given(reservationRepository.saveAndFlush(any())).willAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 1L);
            return r;
        });

        service.create(reqOf(1L, 1L, new int[]{1, 1}, new int[]{1, 2}, new int[]{1, 3}));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSeats()).hasSize(3);
        assertThat(captor.getValue().getSeats())
                .allSatisfy(s -> assertThat(s.getPaidPrice()).isEqualTo(14000));
    }

    // ─── getById ──────────────────────────────────────────────────────────────

    @Test
    void 없는_예매_조회시_RESERVATION_NOT_FOUND() {
        given(reservationRepository.findByIdWithDetails(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 예매_조회_정상() {
        Screening screening = screening8x10With(1L, 14000);
        User user = userWithId(2L);
        Reservation reservation = Reservation.builder().user(user).screening(screening).build();
        ReflectionTestUtils.setField(reservation, "id", 5L);
        reservation.addSeat(1, 1, 14000);
        reservation.addSeat(1, 2, 14000);
        given(reservationRepository.findByIdWithDetails(5L)).willReturn(Optional.of(reservation));

        ReservationResponse response = service.getById(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.seats()).containsExactly("A1", "A2");
        assertThat(response.totalPrice()).isEqualTo(28000);
        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
    }

    // ─── cancel ───────────────────────────────────────────────────────────────

    @Test
    void 없는_예매_취소시_RESERVATION_NOT_FOUND() {
        given(reservationRepository.findByIdWithDetails(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 예매_취소_정상() {
        Screening screening = screening8x10With(1L, 14000);
        User user = userWithId(1L);
        Reservation reservation = Reservation.builder().user(user).screening(screening).build();
        reservation.addSeat(1, 1, 14000);
        reservation.addSeat(1, 2, 14000);
        given(reservationRepository.findByIdWithDetails(1L)).willReturn(Optional.of(reservation));

        service.cancel(1L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCancelledAt()).isNotNull();
        assertThat(reservation.getSeats()).isEmpty(); // orphanRemoval 대상이 됨
    }

    @Test
    void 이미_취소된_예매_취소시_ALREADY_CANCELLED() {
        Screening screening = screening8x10With(1L, 14000);
        User user = userWithId(1L);
        Reservation reservation = Reservation.builder().user(user).screening(screening).build();
        reservation.cancel(); // 미리 취소 상태로 세팅
        given(reservationRepository.findByIdWithDetails(1L)).willReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_CANCELLED);
    }

    // ─── 픽스처 헬퍼 ──────────────────────────────────────────────────────────

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

    private User userWithId(long id) {
        User user = User.builder()
                .loginId("testuser01").password("pw").name("테스트유저")
                .birthDate(LocalDate.of(2000, 1, 1)).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ReservationSeat seatOf(int row, int col) {
        return ReservationSeat.builder()
                .reservation(null).screening(null).rowNum(row).colNum(col).paidPrice(0)
                .build();
    }

    private ReservationCreateRequest reqOf(long screeningId, long userId, int[]... seats) {
        List<ReservationCreateRequest.SeatRequest> seatRequests = Arrays.stream(seats)
                .map(s -> new ReservationCreateRequest.SeatRequest(s[0], s[1]))
                .toList();
        return new ReservationCreateRequest(screeningId, userId, seatRequests);
    }
}
