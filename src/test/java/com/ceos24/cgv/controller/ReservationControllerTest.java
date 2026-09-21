package com.ceos24.cgv.controller;

import com.ceos24.cgv.domain.*;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
 * 커버되지 않는 경로:
 * SEAT_ALREADY_RESERVED는 서비스의 pre-check(step 5)에서 먼저 걸리기 때문에,
 * saveAndFlush + DataIntegrityViolationException → SEAT_ALREADY_RESERVED 변환 경로(step 7)는
 * 이 테스트에서 재현되지 않는다. 그 경로는 두 요청이 동시에 pre-check를 통과했을 때만 도달하는데,
 * 단일 스레드 통합 테스트로는 그 상태를 만들 수 없다.
 * 진짜 검증하려면 별도 동시성 테스트(CountDownLatch + ExecutorService)가 필요하며 이번 범위 밖.
 */
class ReservationControllerTest extends ControllerIntegrationTest {

    private Screening screening;
    private User user;

    @BeforeEach
    void setUp() {
        Branch branch = persist(TestFixtures.branch("강남점"));
        TheaterType tt = persist(TestFixtures.theaterType("일반", 8, 10));
        Theater theater = persist(TestFixtures.theater(branch, tt, "1관"));
        Movie movie = persist(TestFixtures.movie("범죄도시4"));
        screening = persist(TestFixtures.screening(theater, movie, LocalDateTime.of(2024, 6, 1, 10, 0), 14000));
        user = persist(TestFixtures.user("testuser01"));
    }

    private String reserveBody(int row, int col) {
        return """
                {"screeningId":%d,"userId":%d,"seats":[{"rowNum":%d,"colNum":%d}]}
                """.formatted(screening.getId(), user.getId(), row, col);
    }

    @Test
    void 예매_생성_성공() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":%d,"userId":%d,"seats":[{"rowNum":3,"colNum":3},{"rowNum":3,"colNum":4}]}
                                """.formatted(screening.getId(), user.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0]").value("C3"))
                .andExpect(jsonPath("$.seats[1]").value("C4"))
                .andExpect(jsonPath("$.totalPrice").value(28000));
    }

    @Test
    void 존재하지_않는_회차_예매_404() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":9999,"userId":%d,"seats":[{"rowNum":1,"colNum":1}]}
                                """.formatted(user.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCREENING_NOT_FOUND"));
    }

    @Test
    void 존재하지_않는_회원_예매_404() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":%d,"userId":9999,"seats":[{"rowNum":1,"colNum":1}]}
                                """.formatted(screening.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 좌석_범위_초과_400() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(99, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_OUT_OF_RANGE"));
    }

    @Test
    void 요청_내_중복_좌석_400() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":%d,"userId":%d,"seats":[{"rowNum":5,"colNum":5},{"rowNum":5,"colNum":5}]}
                                """.formatted(screening.getId(), user.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SEAT_IN_REQUEST"));
    }

    @Test
    void 이미_예매된_좌석_409() throws Exception {
        Reservation existing = Reservation.builder().user(user).screening(screening).build();
        existing.addSeat(1, 7, 14000);
        persist(existing);
        flushAndClear();

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(1, 7)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));
    }

    @Test
    void Bean_Validation_실패_seats_빈배열_400() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":%d,"userId":%d,"seats":[]}
                                """.formatted(screening.getId(), user.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").exists());
    }

    @Test
    void 예매_단건_조회_성공() throws Exception {
        Reservation reservation = Reservation.builder().user(user).screening(screening).build();
        reservation.addSeat(3, 3, 14000);
        persist(reservation);
        flushAndClear();

        mockMvc.perform(get("/api/reservations/{id}", reservation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservation.getId()))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.seats[0]").value("C3"))
                .andExpect(jsonPath("$.totalPrice").value(14000));
    }

    @Test
    void 없는_예매_조회_404() throws Exception {
        mockMvc.perform(get("/api/reservations/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void 예매_취소_성공_후_조회하면_CANCELLED() throws Exception {
        Reservation reservation = Reservation.builder().user(user).screening(screening).build();
        reservation.addSeat(3, 3, 14000);
        persist(reservation);
        flushAndClear();

        mockMvc.perform(delete("/api/reservations/{id}", reservation.getId()))
                .andExpect(status().isNoContent());

        flushAndClear();

        mockMvc.perform(get("/api/reservations/{id}", reservation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seats.length()").value(0))
                .andExpect(jsonPath("$.totalPrice").value(0));
    }

    @Test
    void 없는_예매_취소_404() throws Exception {
        mockMvc.perform(delete("/api/reservations/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void 이미_취소된_예매_재취소_409() throws Exception {
        Reservation reservation = Reservation.builder().user(user).screening(screening).build();
        reservation.addSeat(3, 3, 14000);
        persist(reservation);
        flushAndClear();

        mockMvc.perform(delete("/api/reservations/{id}", reservation.getId()))
                .andExpect(status().isNoContent());

        flushAndClear();

        mockMvc.perform(delete("/api/reservations/{id}", reservation.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
    }

    @Test
    void 취소한_좌석은_재예매_가능() throws Exception {
        // orphanRemoval로 자식 행이 실제 삭제되어 유니크 제약을 통과한다는 설계 전제 검증
        Reservation reservation = Reservation.builder().user(user).screening(screening).build();
        reservation.addSeat(3, 3, 14000);
        persist(reservation);
        flushAndClear();

        mockMvc.perform(delete("/api/reservations/{id}", reservation.getId()))
                .andExpect(status().isNoContent());

        flushAndClear();

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(3, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats[0]").value("C3"));
    }
}
