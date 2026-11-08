package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossCancelResult;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Rejected;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static com.ceos24.cgv.domain.reservation.entity.AudienceType.ADULT;
import static com.ceos24.cgv.domain.reservation.entity.AudienceType.YOUTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationControllerTest extends ControllerIntegrationTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 9, 0);
    private static final LocalDateTime START = LocalDateTime.of(2024, 6, 1, 12, 0);
    private static final String PAYMENT_KEY = "tgen_test_key";

    // 선점 만료와 취소 기한을 검증하려면 시간을 밀 수 있어야 한다
    @MockitoBean Clock clock;
    // 토스를 실제로 부르지 않는다. 승인·거절·결과 불명을 골라 돌려준다
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    private Screening screening;
    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        setNow(NOW);

        Branch branch = persist(TestFixtures.branch("강남점"));
        Theater theater = persist(TestFixtures.theater(branch, TheaterType.STANDARD, "1관"));
        Movie movie = persist(TestFixtures.movie("범죄도시4"));
        screening = persist(TestFixtures.screening(theater, movie, START, 14000));
        user = persist(TestFixtures.user("testuser01"));
        other = persist(TestFixtures.user("testuser02"));
        flushAndClear();
    }

    // ─── 좌석 선점 ────────────────────────────────────────────────────────────

    @Test
    void 좌석_선점_성공() throws Exception {
        선점요청(seat(1, 1, ADULT), seat(1, 2, YOUTH))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
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
    void 본문에_남의_userId를_실어도_토큰의_사용자로_선점된다() throws Exception {
        String body = "{\"screeningId\":%d,\"userId\":%d,\"seats\":[%s]}"
                .formatted(screening.getId(), other.getId(), seat(1, 1, ADULT));

        mockMvc.perform(post("/api/reservations").with(bearer(user))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(user.getId()));
    }

    @Test
    void 토큰_없이_선점하면_401() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content("{\"screeningId\":%d,\"seats\":[%s]}"
                                .formatted(screening.getId(), seat(1, 1, ADULT))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
    }

    @Test
    void 존재하지_않는_회차_404() throws Exception {
        선점요청(user, 9999L, seat(1, 1, ADULT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCREENING_NOT_FOUND"));
    }

    @Test
    void 토큰은_유효하지만_사용자가_없으면_404() throws Exception {
        선점요청(missingUser(), screening.getId(), seat(1, 1, ADULT))
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

        선점요청(other, screening.getId(), seat(1, 1, ADULT))
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
    void 결제_주문을_만들면_결제위젯에_넘길_값을_준다() throws Exception {
        Long id = 선점(1, 1, 1, 2);

        주문요청(id, user)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(Matchers.startsWith("cgv-r-")))
                .andExpect(jsonPath("$.data.orderName").value("범죄도시4 2매"))
                .andExpect(jsonPath("$.data.amount").value(28000));
    }

    @Test
    void 결제가_승인되면_예매가_확정되고_결제가_기록된다() throws Exception {
        Long id = 선점(1, 1);
        Order order = 주문(id);
        토스가_승인한다();

        승인요청(id, user, order.orderId(), order.amount())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.confirmedAt").value("2024-06-01T09:00:00"));
        flushAndClear();

        Payment payment = 결제조회(order.orderId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(payment.getAmount()).isEqualTo(14000);
    }

    @Test
    void 금액을_조작하면_400이고_토스를_부르지_않는다() throws Exception {
        Long id = 선점(1, 1);
        Order order = 주문(id);

        승인요청(id, user, order.orderId(), 100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    void 주문번호가_토스_규칙에_맞지_않으면_400() throws Exception {
        Long id = 선점(1, 1);

        승인요청(id, user, "cgv1", 14000)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 다른_예매의_주문번호로는_승인할_수_없다() throws Exception {
        Long first = 선점(1, 1);
        Long second = 선점(2, 1);
        Order order = 주문(first);

        승인요청(second, user, order.orderId(), order.amount())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    void 거절되면_402이고_좌석은_유지되어_새_주문으로_다시_결제할_수_있다() throws Exception {
        Long id = 선점(1, 1);
        Order first = 주문(id);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willReturn(new Rejected("REJECT_CARD_COMPANY", "카드사 거절", false));

        승인요청(id, user, first.orderId(), first.amount())
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"));
        flushAndClear();

        assertThat(결제조회(first.orderId()).getStatus()).isEqualTo(PaymentStatus.FAILED);
        조회요청(id, user).andExpect(jsonPath("$.data.status").value("PENDING"));
        선점요청(other, screening.getId(), seat(1, 1, ADULT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));
        // 토스 orderId는 재사용할 수 없어 거절된 주문으로는 다시 승인하지 않는다
        승인요청(id, user, first.orderId(), first.amount())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATUS"));

        Order second = 주문(id);
        토스가_승인한다();
        승인요청(id, user, second.orderId(), second.amount())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    @Test
    void 토스에_닿지_못한_승인은_503이고_결제는_실패로_끝나_새_주문으로_다시_결제할_수_있다() throws Exception {
        Long id = 선점(1, 1);
        Order first = 주문(id);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willReturn(new Rejected("CONNECTION_FAILED", "Connection refused", true));

        승인요청(id, user, first.orderId(), first.amount())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_SERVICE_UNAVAILABLE"));
        flushAndClear();

        // 돈이 빠지지 않았음이 확실하므로 승인 중으로 남기지 않는다. 남기면 정리될 때까지 새 결제가 막힌다
        assertThat(결제조회(first.orderId()).getStatus()).isEqualTo(PaymentStatus.FAILED);
        Order second = 주문(id);
        토스가_승인한다();
        승인요청(id, user, second.orderId(), second.amount())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    @Test
    void 결과를_모르면_504이고_결제는_승인중으로_남아_취소도_새_결제도_막힌다() throws Exception {
        Long id = 선점(1, 1);
        Order order = 주문(id);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willReturn(new Unknown("Read timed out"));

        승인요청(id, user, order.orderId(), order.amount())
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PAYMENT_RESULT_UNKNOWN"));
        flushAndClear();

        // 토스에서는 승인됐을 수 있다. 좌석을 풀거나 다시 결제하게 두면 이중 결제·이중 판매가 된다
        assertThat(결제조회(order.orderId()).getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        취소요청(id, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));
        주문요청(id, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));
    }

    @Test
    void 확정된_예매를_다시_승인하면_409이고_토스는_한_번만_불린다() throws Exception {
        Long id = 선점(1, 1);
        Order order = 주문(id);
        토스가_승인한다();
        승인요청(id, user, order.orderId(), order.amount()).andExpect(status().isOk());
        flushAndClear();

        승인요청(id, user, order.orderId(), order.amount())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_PENDING"));
        verify(tossPaymentsClient, times(1)).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    void 다른_결제에_쓰인_paymentKey로_승인하면_500이_아니라_409이고_토스를_부르지_않는다() throws Exception {
        Long paid = 선점(1, 1);
        결제완료(paid);
        Long id = 선점(1, 2);
        Order order = 주문(id);

        // 승인요청은 늘 같은 PAYMENT_KEY를 싣는다. 이미 승인된 결제의 키다
        승인요청(id, user, order.orderId(), order.amount())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_KEY_ALREADY_USED"));
        // 유니크 위반으로 flush에 실패한 변경이 영속성 컨텍스트에 남아 있어 flush 없이 비운다
        em.clear();

        verify(tossPaymentsClient, times(1)).confirm(anyString(), anyString(), anyInt());
        assertThat(결제조회(order.orderId()).getStatus()).isEqualTo(PaymentStatus.READY);
    }

    // ─── 결제 조회 ────────────────────────────────────────────────────────────

    @Test
    void 결제_조회는_거절과_승인_시도를_최근_순으로_보여주고_떠난_주문은_뺀다() throws Exception {
        Long id = 선점(1, 1);
        주문(id);   // 결제창만 열고 떠난 주문
        Order rejected = 주문(id);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willReturn(new Rejected("REJECT_CARD_COMPANY", "카드사 거절", false));
        승인요청(id, user, rejected.orderId(), rejected.amount()).andExpect(status().isPaymentRequired());
        Order approved = 주문(id);
        토스가_승인한다();
        승인요청(id, user, approved.orderId(), approved.amount()).andExpect(status().isOk());
        flushAndClear();

        결제조회요청(id, user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].orderId").value(approved.orderId()))
                .andExpect(jsonPath("$.data[0].status").value("DONE"))
                .andExpect(jsonPath("$.data[0].amount").value(14000))
                .andExpect(jsonPath("$.data[0].method").value("카드"))
                .andExpect(jsonPath("$.data[0].approvedAt").value("2024-06-01T09:00:00"))
                .andExpect(jsonPath("$.data[1].orderId").value(rejected.orderId()))
                .andExpect(jsonPath("$.data[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data[1].failureCode").value("REJECT_CARD_COMPANY"));
    }

    @Test
    void 결제_조회는_토스를_부르지_않는다() throws Exception {
        Long id = 선점(1, 1);
        결제완료(id);

        결제조회요청(id, user).andExpect(status().isOk());

        verify(tossPaymentsClient, never()).findByOrderId(anyString());
    }

    @Test
    void 남의_예매의_결제는_조회되지_않는다() throws Exception {
        Long id = 선점(1, 1);
        결제완료(id);

        결제조회요청(id, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    // ─── 선점 만료 ────────────────────────────────────────────────────────────

    @Test
    void 만료된_선점에는_결제_주문을_만들_수_없다() throws Exception {
        Long id = 선점(1, 1);
        setNow(NOW.plusMinutes(11));

        주문요청(id, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));
    }

    @Test
    void 주문_뒤에_선점이_만료되면_토스를_부르지_않는다() throws Exception {
        // 토스 인증은 아직 유효해도 좌석은 이미 다른 사람이 가져갈 수 있는 상태다
        Long id = 선점(1, 1);
        Order order = 주문(id);
        setNow(NOW.plusMinutes(11));

        승인요청(id, user, order.orderId(), order.amount())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    void 만료된_선점의_좌석은_다시_잡을_수_있다() throws Exception {
        Long id = 선점(1, 1);
        setNow(NOW.plusMinutes(11));

        선점요청(seat(1, 1, ADULT)).andExpect(status().isCreated());
        flushAndClear();

        조회요청(id, user)
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

        조회요청(id, user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.screening.movieTitle").value("범죄도시4"))
                .andExpect(jsonPath("$.data.screening.branchName").value("강남점"));
    }

    @Test
    void 없는_예매_조회_404() throws Exception {
        조회요청(9999L, user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void 취소해도_어느_좌석을_얼마에_잡았는지가_남는다() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        토스가_취소한다(28000);

        취소요청(id, user).andExpect(status().isOk());
        flushAndClear();

        조회요청(id, user)
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledAt").exists())
                .andExpect(jsonPath("$.data.seats.length()").value(2))
                .andExpect(jsonPath("$.data.totalPrice").value(28000));
    }

    @Test
    void 취소한_좌석은_재예매_가능() throws Exception {
        Long id = 선점(1, 1);
        취소요청(id, user).andExpect(status().isOk());
        flushAndClear();

        선점요청(seat(1, 1, ADULT)).andExpect(status().isCreated());
    }

    @Test
    void 이미_취소된_예매_재취소_409() throws Exception {
        Long id = 선점(1, 1);
        취소요청(id, user).andExpect(status().isOk());
        flushAndClear();

        취소요청(id, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
    }

    @Test
    void 없는_예매_취소_404() throws Exception {
        취소요청(9999L, user)
                .andExpect(status().isNotFound());
    }

    @Test
    void 상영_20분_이내면_확정된_예매를_취소할_수_없다() throws Exception {
        Long id = 선점(1, 1);
        결제완료(id);
        setNow(START.minusMinutes(19));

        취소요청(id, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_DEADLINE_PASSED"));
    }

    @Test
    void 상영_21분_전에는_취소된다() throws Exception {
        Long id = 선점(1, 1);
        결제완료(id);
        setNow(START.minusMinutes(21));
        토스가_취소한다(14000);

        취소요청(id, user)
                .andExpect(status().isOk());
    }

    // ─── 결제 취소·환불 ───────────────────────────────────────────────────────

    @Test
    void 결제된_예매를_전체_취소하면_토스로_전액_환불하고_좌석을_놓는다() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        토스가_취소한다(28000);

        취소요청(id, user).andExpect(status().isOk());
        flushAndClear();

        // 금액은 서버가 좌석 가격으로 계산하고, 사유도 서버가 만든다
        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), eq(28000), eq("예매 전체 취소: A1, A2"));
        조회요청(id, user)
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.totalPrice").value(28000))
                .andExpect(jsonPath("$.data.remainingPrice").value(0))
                .andExpect(jsonPath("$.data.seats[0].cancelled").value(true))
                .andExpect(jsonPath("$.data.seats[1].cancelled").value(true));
        결제조회요청(id, user)
                .andExpect(jsonPath("$.data[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.data[0].canceledAmount").value(28000))
                .andExpect(jsonPath("$.data[0].balanceAmount").value(0))
                .andExpect(jsonPath("$.data[0].cancels.length()").value(1))
                .andExpect(jsonPath("$.data[0].cancels[0].status").value("DONE"))
                .andExpect(jsonPath("$.data[0].cancels[0].amount").value(28000));
        선점요청(other, screening.getId(), seat(1, 1, ADULT)).andExpect(status().isCreated());
    }

    @Test
    void 좌석을_나눠_여러_번_취소하면_잔액이_줄고_마지막_좌석까지_취소하면_예매가_취소된다() throws Exception {
        Long id = 선점(1, 1, 1, 2, 1, 3);
        결제완료(id);
        토스가_취소한다(42000);

        좌석취소요청(id, user, seatPos(1, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.totalPrice").value(42000))
                .andExpect(jsonPath("$.data.remainingPrice").value(28000))
                .andExpect(jsonPath("$.data.seats[1].label").value("A2"))
                .andExpect(jsonPath("$.data.seats[1].cancelled").value(true))
                .andExpect(jsonPath("$.data.seats[0].cancelled").value(false));
        flushAndClear();
        결제조회요청(id, user)
                .andExpect(jsonPath("$.data[0].status").value("PARTIAL_CANCELED"))
                .andExpect(jsonPath("$.data[0].canceledAmount").value(14000))
                .andExpect(jsonPath("$.data[0].balanceAmount").value(28000));

        좌석취소요청(id, user, seatPos(1, 1), seatPos(1, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.remainingPrice").value(0));
        flushAndClear();
        결제조회요청(id, user)
                .andExpect(jsonPath("$.data[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.data[0].balanceAmount").value(0))
                .andExpect(jsonPath("$.data[0].cancels.length()").value(2))
                .andExpect(jsonPath("$.data[0].cancels[0].reason").value("예매 부분 취소: A2"))
                .andExpect(jsonPath("$.data[0].cancels[1].reason").value("예매 부분 취소: A1, A3"));
        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), eq(14000), anyString());
        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), eq(28000), anyString());
    }

    @Test
    void 부분_취소한_좌석은_다른_회원이_잡을_수_있고_예매율에서도_빠진다() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        토스가_취소한다(28000);

        좌석취소요청(id, user, seatPos(1, 1)).andExpect(status().isOk());
        flushAndClear();

        mockMvc.perform(get("/api/movies/{id}", screening.getMovie().getId()))
                .andExpect(jsonPath("$.data.reservedSeatCount").value(1));
        선점요청(other, screening.getId(), seat(1, 1, ADULT)).andExpect(status().isCreated());
        선점요청(other, screening.getId(), seat(1, 2, ADULT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));
    }

    @Test
    void 이미_취소된_좌석을_다시_취소하면_409이고_토스를_부르지_않는다() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        토스가_취소한다(28000);
        좌석취소요청(id, user, seatPos(1, 1)).andExpect(status().isOk());
        flushAndClear();

        좌석취소요청(id, user, seatPos(1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_CANCELLED"));
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 전체_취소된_예매를_다시_취소하면_409이고_토스를_부르지_않는다() throws Exception {
        Long id = 선점(1, 1);
        결제완료(id);
        토스가_취소한다(14000);
        취소요청(id, user).andExpect(status().isOk());
        flushAndClear();

        취소요청(id, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
        좌석취소요청(id, user, seatPos(1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 이_예매의_좌석이_아니거나_요청에_중복이_있으면_400() throws Exception {
        Long id = 선점(1, 1);
        결제완료(id);

        좌석취소요청(id, user, seatPos(5, 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_RESERVATION"));
        좌석취소요청(id, user, seatPos(1, 1), seatPos(1, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SEAT_IN_REQUEST"));
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 결제_전_선점은_부분_취소할_수_없다() throws Exception {
        Long id = 선점(1, 1, 1, 2);

        좌석취소요청(id, user, seatPos(1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_RESERVED"));
    }

    @Test
    void 상영_20분_이내면_부분_취소도_할_수_없다() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        setNow(START.minusMinutes(19));

        좌석취소요청(id, user, seatPos(1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_DEADLINE_PASSED"));
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 토스가_취소를_거절하면_좌석과_결제는_그대로이고_다시_취소할_수_있다() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Rejected("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다."));

        좌석취소요청(id, user, seatPos(1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_REJECTED"));
        flushAndClear();

        조회요청(id, user)
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.remainingPrice").value(28000));
        결제조회요청(id, user)
                .andExpect(jsonPath("$.data[0].status").value("DONE"))
                .andExpect(jsonPath("$.data[0].balanceAmount").value(28000))
                .andExpect(jsonPath("$.data[0].cancels[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].cancels[0].failureCode").value("NOT_CANCELABLE_PAYMENT"));
        선점요청(other, screening.getId(), seat(1, 1, ADULT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));

        토스가_취소한다(28000);
        좌석취소요청(id, user, seatPos(1, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingPrice").value(14000));
    }

    @Test
    void 취소_결과를_모르면_504이고_좌석을_놓지_않으며_같은_멱등키로_재시도하면_반영된다() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));

        좌석취소요청(id, user, seatPos(1, 1))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_RESULT_UNKNOWN"));
        flushAndClear();

        // 토스에서는 환불됐을 수 있다. 그래도 좌석을 놓으면, 실제로는 환불되지 않았을 때 회원이 둘 다 잃는다
        선점요청(other, screening.getId(), seat(1, 1, ADULT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));
        // 새 취소를 받으면 새 멱등키로 같은 금액이 한 번 더 환불될 수 있다
        좌석취소요청(id, user, seatPos(1, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_IN_PROGRESS"));
        Long cancelId = 취소요청Id(id);

        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(TestFixtures.tossCanceled(PAYMENT_KEY, 28000, 14000, 14000));
        재시도요청(id, cancelId, user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingPrice").value(14000))
                .andExpect(jsonPath("$.data.seats[0].cancelled").value(true));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasons = ArgumentCaptor.forClass(String.class);
        verify(tossPaymentsClient, times(2)).cancel(eq(PAYMENT_KEY), keys.capture(), eq(14000), reasons.capture());
        assertThat(keys.getAllValues().get(1)).isEqualTo(keys.getAllValues().get(0));
        assertThat(reasons.getAllValues().get(1)).isEqualTo(reasons.getAllValues().get(0));
        flushAndClear();
        결제조회요청(id, user)
                .andExpect(jsonPath("$.data[0].status").value("PARTIAL_CANCELED"))
                .andExpect(jsonPath("$.data[0].cancels.length()").value(1))
                .andExpect(jsonPath("$.data[0].cancels[0].status").value("DONE"));
    }

    @Test
    void 결과가_정해진_취소는_재시도할_수_없다() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        토스가_취소한다(28000);
        좌석취소요청(id, user, seatPos(1, 1)).andExpect(status().isOk());
        flushAndClear();

        재시도요청(id, 취소요청Id(id), user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_NOT_RETRYABLE"));
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 요청_후_14일이_지난_취소는_재시도하지_않는다() throws Exception {
        // 토스 멱등키는 15일 유효하다. 그 뒤에 같은 키로 보내면 새 요청으로 처리되어 두 번 환불될 수 있다
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));
        좌석취소요청(id, user, seatPos(1, 1)).andExpect(status().isGatewayTimeout());
        flushAndClear();
        Long cancelId = 취소요청Id(id);

        setNow(NOW.plusDays(14));
        재시도요청(id, cancelId, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_RETRY_EXPIRED"));
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 남의_예매는_부분_취소도_재시도도_404() throws Exception {
        Long id = 선점(1, 1, 1, 2);
        결제완료(id);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));
        좌석취소요청(id, user, seatPos(1, 1)).andExpect(status().isGatewayTimeout());
        flushAndClear();
        Long cancelId = 취소요청Id(id);

        좌석취소요청(id, other, seatPos(1, 2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        재시도요청(id, cancelId, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        // 내 예매 경로에 남의 취소 id를 넣어도 찾지 못한다
        Long mine = 선점Of(other, 3, 3);
        재시도요청(mine, cancelId, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_NOT_FOUND"));
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    // ─── 내 예매 내역 ─────────────────────────────────────────────────────────

    @Test
    void 내_예매_내역은_내_것만_최근_순으로() throws Exception {
        Long first = 선점(1, 1, 1, 2);
        Long othersId = 선점Of(other, 3, 3);
        Long second = 선점(2, 1);

        내역요청(user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(second))
                .andExpect(jsonPath("$.data[0].seats.length()").value(1))
                .andExpect(jsonPath("$.data[1].id").value(first))
                .andExpect(jsonPath("$.data[1].seats[0].label").value("A1"))
                .andExpect(jsonPath("$.data[1].seats[1].label").value("A2"))
                .andExpect(jsonPath("$.data[1].totalPrice").value(28000))
                .andExpect(jsonPath("$.data[*].id", Matchers.not(Matchers.hasItem(othersId.intValue()))));
    }

    @Test
    void 취소되거나_만료된_예매도_상태와_함께_남는다() throws Exception {
        Long cancelled = 선점(1, 1);
        취소요청(cancelled, user).andExpect(status().isOk());
        Long expired = 선점(2, 1);
        flushAndClear();
        setNow(NOW.plusMinutes(11));

        내역요청(user)
                .andExpect(jsonPath("$.data[0].id").value(expired))
                .andExpect(jsonPath("$.data[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.data[1].id").value(cancelled))
                .andExpect(jsonPath("$.data[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[1].seats.length()").value(1));
    }

    @Test
    void 예매가_없으면_빈_배열() throws Exception {
        내역요청(user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 사라진_사용자의_토큰으로_내역을_조회하면_404() throws Exception {
        내역요청(missingUser())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ─── 소유권 ───────────────────────────────────────────────────────────────
    // 남의 예매는 없는 예매와 구분되지 않아야 한다. 응답이 다르면 id를 차례로 넣어
    // 어떤 예매가 존재하는지 알아낼 수 있다.

    @Test
    void 남의_예매는_조회되지_않고_응답이_없는_예매와_같다() throws Exception {
        Long id = 선점(1, 1);

        String othersReservation = 조회요청(id, other)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missingReservation = 조회요청(9999L, other)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(othersReservation).isEqualTo(missingReservation);
    }

    @Test
    void 남의_예매를_취소할_수_없고_상태도_바뀌지_않는다() throws Exception {
        Long id = 선점(1, 1);
        결제완료(id);

        취소요청(id, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        flushAndClear();

        조회요청(id, user)
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.cancelledAt").doesNotExist());
    }

    @Test
    void 남의_예매로는_결제_주문도_승인도_할_수_없다() throws Exception {
        // 주문번호를 알아내도 소용없어야 한다. 남의 결제창 인증 결과로 내 좌석이 확정되면 안 된다
        Long id = 선점(1, 1);
        Order order = 주문(id);

        주문요청(id, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        승인요청(id, other, order.orderId(), order.amount())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        flushAndClear();

        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(결제조회(order.orderId()).getStatus()).isEqualTo(PaymentStatus.READY);
        조회요청(id, user).andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void 남의_취소된_예매를_취소하면_409가_아니라_404() throws Exception {
        // 상태 검사가 먼저면 409가 나와 남의 예매가 존재하고 이미 취소됐다는 사실이 드러난다
        Long id = 선점(1, 1);
        취소요청(id, user).andExpect(status().isOk());
        flushAndClear();

        취소요청(id, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
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
        return 선점요청(user, screening.getId(), seats);
    }

    private ResultActions 선점요청(User requester, Long screeningId, String... seats) throws Exception {
        String body = "{\"screeningId\":%d,\"seats\":[%s]}"
                .formatted(screeningId, String.join(",", seats));
        return mockMvc.perform(post("/api/reservations").with(bearer(requester))
                .contentType("application/json")
                .content(body));
    }

    private record Order(String orderId, int amount) {
    }

    private ResultActions 주문요청(Long id, User requester) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/payments", id).with(bearer(requester)));
    }

    private Order 주문(Long id) throws Exception {
        String body = 주문요청(id, user)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Order(JsonPath.read(body, "$.data.orderId"), JsonPath.<Integer>read(body, "$.data.amount"));
    }

    // successUrl로 돌아온 값을 그대로 서버에 넘기는 요청이다
    private ResultActions 승인요청(Long id, User requester, String orderId, int amount) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/payments/confirm", id).with(bearer(requester))
                .contentType("application/json")
                .content("{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":%d}"
                        .formatted(PAYMENT_KEY, orderId, amount)));
    }

    private void 토스가_승인한다() {
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> new Approved(
                TestFixtures.approvedToss(inv.getArgument(0), inv.getArgument(1), inv.<Integer>getArgument(2),
                        OffsetDateTime.of(2024, 6, 1, 9, 0, 0, 0, ZoneOffset.ofHours(9)))));
    }

    private void 결제완료(Long id) throws Exception {
        Order order = 주문(id);
        토스가_승인한다();
        승인요청(id, user, order.orderId(), order.amount()).andExpect(status().isOk());
        flushAndClear();
    }

    private Payment 결제조회(String orderId) {
        return em.createQuery("select p from Payment p where p.orderId = :orderId", Payment.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    private void 토스가_취소한다(int paidAmount) {
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willAnswer(TestFixtures.tossCancelsFrom(paidAmount));
    }

    private String seatPos(int rowNum, int colNum) {
        return "{\"rowNum\":%d,\"colNum\":%d}".formatted(rowNum, colNum);
    }

    private ResultActions 좌석취소요청(Long id, User requester, String... seats) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/cancellations", id).with(bearer(requester))
                .contentType("application/json")
                .content("{\"seats\":[%s]}".formatted(String.join(",", seats))));
    }

    private ResultActions 재시도요청(Long id, Long cancelId, User requester) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/cancellations/{cancelId}/retry", id, cancelId)
                .with(bearer(requester)));
    }

    // 결제 조회 응답에서 가장 최근 취소 요청의 id를 꺼낸다. 회원이 재시도할 때 쓰는 값이다
    private Long 취소요청Id(Long id) throws Exception {
        String body = 결제조회요청(id, user).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.data[0].cancels[*].id");
        return ids.getLast().longValue();
    }

    private ResultActions 결제조회요청(Long id, User requester) throws Exception {
        return mockMvc.perform(get("/api/reservations/{id}/payments", id).with(bearer(requester)));
    }

    private ResultActions 조회요청(Long id, User requester) throws Exception {
        return mockMvc.perform(get("/api/reservations/{id}", id).with(bearer(requester)));
    }

    private ResultActions 취소요청(Long id, User requester) throws Exception {
        return mockMvc.perform(delete("/api/reservations/{id}", id).with(bearer(requester)));
    }

    private ResultActions 내역요청(User requester) throws Exception {
        return mockMvc.perform(get("/api/reservations").with(bearer(requester)));
    }

    // API를 거치지 않고 선점 상태를 만들어 둔다
    private Long 선점(int... rowCols) {
        return 선점Of(user, rowCols);
    }

    private Long 선점Of(User owner, int... rowCols) {
        Reservation reservation = TestFixtures.hold(
                em.find(User.class, owner.getId()),
                em.find(Screening.class, screening.getId()),
                LocalDateTime.now(clock));
        for (int i = 0; i < rowCols.length; i += 2) {
            reservation.addSeat(rowCols[i], rowCols[i + 1], ADULT);
        }
        persist(reservation);
        flushAndClear();
        return reservation.getId();
    }
}
