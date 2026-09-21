package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.IntStream;

import static com.ceos24.cgv.domain.reservation.entity.AudienceType.ADULT;
import static com.ceos24.cgv.domain.reservation.entity.AudienceType.YOUTH;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationControllerTest extends ControllerIntegrationTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 9, 0);
    private static final LocalDateTime START = LocalDateTime.of(2024, 6, 1, 12, 0);

    // 선점 만료와 취소 기한을 검증하려면 시간을 밀 수 있어야 한다
    @MockitoBean Clock clock;

    private Screening screening;
    private User user;

    @BeforeEach
    void setUp() {
        setNow(NOW);

        Branch branch = persist(TestFixtures.branch("강남점"));
        Theater theater = persist(TestFixtures.theater(branch, TheaterType.STANDARD, "1관"));
        Movie movie = persist(TestFixtures.movie("범죄도시4"));
        screening = persist(TestFixtures.screening(theater, movie, START, 14000));
        user = persist(TestFixtures.user("testuser01"));
        flushAndClear();
    }

    // ─── 좌석 선점 ────────────────────────────────────────────────────────────

    @Test
    void 좌석_선점_성공() throws Exception {
        선점요청(seat(1, 1, ADULT), seat(1, 2, YOUTH))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.statusName").value("결제대기"))
                .andExpect(jsonPath("$.data.expiresAt").value("2024-06-01T09:10:00"))
                .andExpect(jsonPath("$.data.confirmedAt").doesNotExist())
                .andExpect(jsonPath("$.data.seats.length()").value(2))
                .andExpect(jsonPath("$.data.seats[0].label").value("A1"))
                .andExpect(jsonPath("$.data.seats[0].paidPrice").value(14000))
                .andExpect(jsonPath("$.data.seats[1].label").value("A2"))
                .andExpect(jsonPath("$.data.seats[1].audienceTypeName").value("청소년"))
                .andExpect(jsonPath("$.data.seats[1].paidPrice").value(11200))
                .andExpect(jsonPath("$.data.totalPrice").value(25200));
    }

    @Test
    void 존재하지_않는_회차_404() throws Exception {
        선점요청(9999L, user.getId(), seat(1, 1, ADULT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCREENING_NOT_FOUND"));
    }

    @Test
    void 존재하지_않는_사용자_404() throws Exception {
        선점요청(screening.getId(), 9999L, seat(1, 1, ADULT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 좌석_범위_초과_400() throws Exception {
        선점요청(seat(9, 1, ADULT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_OUT_OF_RANGE"));
    }

    @Test
    void 요청_내_중복_좌석_400() throws Exception {
        선점요청(seat(1, 1, ADULT), seat(1, 1, ADULT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SEAT_IN_REQUEST"));
    }

    @Test
    void 선점_중인_좌석은_다른_사람이_잡을_수_없다() throws Exception {
        선점(1, 1);

        선점요청(seat(1, 1, ADULT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));
    }

    @Test
    void 좌석을_하나도_고르지_않으면_400() throws Exception {
        선점요청()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 좌석을_9개_이상_고르면_400() throws Exception {
        String[] seats = IntStream.rangeClosed(1, 9)
                .mapToObj(col -> seat(1, col, ADULT))
                .toArray(String[]::new);

        선점요청(seats)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    // ─── 결제 ─────────────────────────────────────────────────────────────────

    @Test
    void 결제_성공하면_예매가_확정된다() throws Exception {
        Long id = 선점(1, 1);

        결제요청(id, "SUCCESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.confirmedAt").value("2024-06-01T09:00:00"));
    }

    @Test
    void 결제_실패하면_402와_함께_좌석이_풀린다() throws Exception {
        Long id = 선점(1, 1);

        결제요청(id, "FAILURE")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));
        flushAndClear();

        mockMvc.perform(get("/api/reservations/{id}", id))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        // 좌석 선택부터 다시 할 수 있어야 한다
        선점요청(seat(1, 1, ADULT)).andExpect(status().isCreated());
    }

    @Test
    void 확정된_예매를_다시_결제하면_409() throws Exception {
        Long id = 선점(1, 1);
        결제(id, "SUCCESS");

        결제요청(id, "SUCCESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_PENDING"));
    }

    // ─── 선점 만료 ────────────────────────────────────────────────────────────

    @Test
    void 만료된_선점은_결제할_수_없다() throws Exception {
        Long id = 선점(1, 1);
        setNow(NOW.plusMinutes(11));

        결제요청(id, "SUCCESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));
    }

    @Test
    void 만료된_선점의_좌석은_다시_잡을_수_있다() throws Exception {
        Long id = 선점(1, 1);
        setNow(NOW.plusMinutes(11));

        선점요청(seat(1, 1, ADULT)).andExpect(status().isCreated());
        flushAndClear();

        mockMvc.perform(get("/api/reservations/{id}", id))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
    }

    @Test
    void 만료된_선점은_회차_좌석_조회에서도_빠진다() throws Exception {
        선점(1, 1);

        mockMvc.perform(get("/api/screenings/{id}/seats", screening.getId()))
                .andExpect(jsonPath("$.data.reservedSeats[0]").value("A1"));

        setNow(NOW.plusMinutes(11));
        mockMvc.perform(get("/api/screenings/{id}/seats", screening.getId()))
                .andExpect(jsonPath("$.data.reservedSeats.length()").value(0));
    }

    // ─── 조회 / 취소 ──────────────────────────────────────────────────────────

    @Test
    void 예매_단건_조회() throws Exception {
        Long id = 선점(1, 1);

        mockMvc.perform(get("/api/reservations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.screening.movieTitle").value("범죄도시4"))
                .andExpect(jsonPath("$.data.screening.branchName").value("강남점"));
    }

    @Test
    void 없는_예매_조회_404() throws Exception {
        mockMvc.perform(get("/api/reservations/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void 취소해도_어느_좌석을_얼마에_잡았는지가_남는다() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제(id, "SUCCESS");

        mockMvc.perform(delete("/api/reservations/{id}", id)).andExpect(status().isOk());
        flushAndClear();

        mockMvc.perform(get("/api/reservations/{id}", id))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledAt").exists())
                .andExpect(jsonPath("$.data.seats.length()").value(2))
                .andExpect(jsonPath("$.data.totalPrice").value(28000));
    }

    @Test
    void 취소한_좌석은_재예매_가능() throws Exception {
        Long id = 선점(1, 1);
        mockMvc.perform(delete("/api/reservations/{id}", id)).andExpect(status().isOk());
        flushAndClear();

        선점요청(seat(1, 1, ADULT)).andExpect(status().isCreated());
    }

    @Test
    void 이미_취소된_예매_재취소_409() throws Exception {
        Long id = 선점(1, 1);
        mockMvc.perform(delete("/api/reservations/{id}", id)).andExpect(status().isOk());
        flushAndClear();

        mockMvc.perform(delete("/api/reservations/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
    }

    @Test
    void 없는_예매_취소_404() throws Exception {
        mockMvc.perform(delete("/api/reservations/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 상영_20분_이내면_확정된_예매를_취소할_수_없다() throws Exception {
        Long id = 선점(1, 1);
        결제(id, "SUCCESS");
        setNow(START.minusMinutes(19));

        mockMvc.perform(delete("/api/reservations/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_DEADLINE_PASSED"));
    }

    @Test
    void 상영_21분_전에는_취소된다() throws Exception {
        Long id = 선점(1, 1);
        결제(id, "SUCCESS");
        setNow(START.minusMinutes(21));

        mockMvc.perform(delete("/api/reservations/{id}", id))
                .andExpect(status().isOk());
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void setNow(LocalDateTime now) {
        given(clock.instant()).willReturn(now.atZone(ZONE).toInstant());
        given(clock.getZone()).willReturn(ZONE);
    }

    private String seat(int rowNum, int colNum, AudienceType audienceType) {
        return "{\"rowNum\":%d,\"colNum\":%d,\"audienceType\":\"%s\"}"
                .formatted(rowNum, colNum, audienceType);
    }

    private ResultActions 선점요청(String... seats) throws Exception {
        return 선점요청(screening.getId(), user.getId(), seats);
    }

    private ResultActions 선점요청(Long screeningId, Long userId, String... seats) throws Exception {
        String body = "{\"screeningId\":%d,\"userId\":%d,\"seats\":[%s]}"
                .formatted(screeningId, userId, String.join(",", seats));
        return mockMvc.perform(post("/api/reservations")
                .contentType("application/json")
                .content(body));
    }

    private ResultActions 결제요청(Long id, String result) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/payment", id)
                .contentType("application/json")
                .content("{\"result\":\"%s\"}".formatted(result)));
    }

    private void 결제(Long id, String result) throws Exception {
        결제요청(id, result);
        flushAndClear();
    }

    // API를 거치지 않고 선점 상태를 만들어 둔다
    private Long 선점(int... rowCols) {
        Reservation reservation = TestFixtures.hold(
                em.find(User.class, user.getId()),
                em.find(Screening.class, screening.getId()),
                LocalDateTime.now(clock));
        for (int i = 0; i < rowCols.length; i += 2) {
            reservation.addSeat(rowCols[i], rowCols[i + 1], ADULT, screening.getPrice());
        }
        persist(reservation);
        flushAndClear();
        return reservation.getId();
    }
}
