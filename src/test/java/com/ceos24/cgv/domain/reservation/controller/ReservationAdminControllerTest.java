package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.entity.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.reservation.service.ReservationPaymentRecoveryService;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossCancelResult;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossLookupResult.Found;
import com.ceos24.cgv.global.client.TossLookupResult.NotFound;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.ceos24.cgv.domain.reservation.entity.AudienceType.ADULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 결과를 모르는 예매 결제를 토스 조회로 정리한다. 승인 응답이 끊겨 회원이 504를 받은 상태에서 시작한다.
// 토스 승인 뒤 T2가 실패한 경우도 DB에 남는 모습(결제 IN_PROGRESS + paymentKey)이 같다.
class ReservationAdminControllerTest extends ControllerIntegrationTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 9, 0);
    private static final LocalDateTime START = LocalDateTime.of(2024, 6, 1, 12, 0);
    private static final String PAYMENT_KEY = "tgen_test_key";

    // ReservationControllerTest와 같은 목 구성이라 같은 컨텍스트를 쓴다
    @MockitoBean Clock clock;
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    @Autowired ReservationPaymentRecoveryService recoveryService;
    @Autowired ReservationService reservationService;

    private Screening screening;
    private User user;
    private User other;
    private User admin;

    @BeforeEach
    void setUp() {
        setNow(NOW);

        Branch branch = persist(TestFixtures.branch("강남점"));
        Theater theater = persist(TestFixtures.theater(branch, TheaterType.STANDARD, "1관"));
        Movie movie = persist(TestFixtures.movie("범죄도시4"));
        screening = persist(TestFixtures.screening(theater, movie, START, 14000));
        user = persist(TestFixtures.user("testuser01"));
        other = persist(TestFixtures.user("testuser02"));
        admin = persist(TestFixtures.admin("admin01"));
        flushAndClear();
    }

    @Test
    void 승인을_시작한_지_2분이_지나기_전에는_정리하지_않는다() throws Exception {
        // 원래 요청이 아직 토스를 기다리고 있을 수 있다. 그 요청의 T2와 함께 확정하면 안 된다
        String orderId = 결과불명결제();
        setNow(NOW.plusSeconds(119));

        정리요청(orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));
        recoveryService.recoverStale();
        verify(tossPaymentsClient, never()).findByPaymentKey(anyString());
    }

    @Test
    void 토스가_승인했으면_예매를_확정하고_막혀_있던_결제가_풀린다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(2));
        토스조회결과_승인(orderId);

        정리요청(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPROVED"));
        flushAndClear();

        Payment payment = 결제조회(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(예매조회(orderId).getStatus()).isEqualTo(ReservationStatus.RESERVED);
        // 같은 결제를 다시 정리하면 이미 결과가 정해진 결제다
        정리요청(orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATUS"));
    }

    @Test
    void 선점_만료_시각이_지났어도_좌석이_풀리지_않았으면_확정한다() throws Exception {
        // 선점 10분 + 아무도 그 좌석을 고르지 않았다. 좌석 행은 아직 이 예매의 것이다
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(15));
        토스조회결과_승인(orderId);

        정리요청(orderId).andExpect(jsonPath("$.data.outcome").value("APPROVED"));
        flushAndClear();

        assertThat(예매조회(orderId).getStatus()).isEqualTo(ReservationStatus.RESERVED);
        // 확정된 좌석이라 다른 회원이 고를 수 없다
        선점요청(other).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));
    }

    @Test
    void 좌석을_잃었으면_전액_환불하고_예매는_선점만료로_남는다() throws Exception {
        String orderId = 결과불명결제();
        // 선점 만료(9:10) 뒤 다른 회원이 같은 좌석을 골라 이 예매를 EXPIRED로 바꾸고 좌석을 가져간다
        setNow(NOW.plusMinutes(11));
        선점요청(other).andExpect(status().isCreated());
        토스조회결과_승인(orderId);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willAnswer(TestFixtures.tossCancelsFrom(14000));

        정리요청(orderId).andExpect(jsonPath("$.data.outcome").value("REFUNDED"));
        flushAndClear();

        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), eq(14000), eq("좌석 선점 만료로 자동 환불"));
        Payment payment = 결제조회(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getBalanceAmount()).isZero();
        // 회원이 취소한 것이 아니다. CANCELLED로 바꾸면 좌석이 왜 풀렸는지 알 수 없다
        Reservation lost = 예매조회(orderId);
        assertThat(lost.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(lost.getCancelledAt()).isNull();
    }

    @Test
    void 상영이_시작된_뒤에_승인이_확인되면_확정하지_않고_전액_환불한다() throws Exception {
        // 정리가 늦게 돌아 좌석은 남아 있지만 이미 볼 수 없는 회차다. 확정하면 취소 기한도 지나 환불받을 길이 없다
        String orderId = 결과불명결제();
        setNow(START.plusMinutes(1));
        토스조회결과_승인(orderId);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willAnswer(TestFixtures.tossCancelsFrom(14000));

        정리요청(orderId)
                .andExpect(jsonPath("$.data.outcome").value("REFUNDED"))
                .andExpect(jsonPath("$.data.detail").value("상영 시작 후 결제 확인으로 자동 환불"));
        flushAndClear();

        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), eq(14000), eq("상영 시작 후 결제 확인으로 자동 환불"));
        assertThat(결제조회(orderId).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        Reservation expired = 예매조회(orderId);
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(expired.getSeats()).noneMatch(seat -> seat.isOccupied());
    }

    @Test
    void 자동_정리_기간이_지난_결제는_스케줄러가_묻지_않고_관리_대상으로_남는다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plus(PaymentRepository.AUTO_RECOVERY_PERIOD).plusSeconds(1));

        recoveryService.recoverStale();
        flushAndClear();

        verify(tossPaymentsClient, never()).findByPaymentKey(anyString());
        assertThat(결제조회(orderId).getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(reservationService.findAbandonedApprovalOrderIds()).containsExactly(orderId);
        assertThat(reservationService.findStaleApprovalOrderIds()).isEmpty();
    }

    @Test
    void 자동_환불_결과를_모르면_스케줄러가_같은_멱등키로_다시_보내_마친다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(11));
        선점요청(other).andExpect(status().isCreated());
        토스조회결과_승인(orderId);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));

        정리요청(orderId).andExpect(jsonPath("$.data.outcome").value("REFUND_PENDING"));
        flushAndClear();
        // 승인은 확정됐고 환불 요청이 남아 있다. 서버가 여기서 죽어도 이 요청으로 이어 간다
        assertThat(결제조회(orderId).getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(취소요청(orderId).getStatus()).isEqualTo(PaymentCancelStatus.IN_PROGRESS);

        setNow(NOW.plusMinutes(14));
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willAnswer(TestFixtures.tossCancelsFrom(14000));
        recoveryService.recoverStale();
        flushAndClear();

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(tossPaymentsClient, times(2)).cancel(eq(PAYMENT_KEY), keys.capture(), eq(14000), anyString());
        assertThat(keys.getAllValues()).hasSize(2).allMatch(key -> key.equals(keys.getAllValues().getFirst()));
        assertThat(결제조회(orderId).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(예매조회(orderId).getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void 토스에_없는_결제면_실패로_확정되어_새_주문으로_다시_결제할_수_있다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(3));
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY))
                .willReturn(new NotFound("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));

        정리요청(orderId).andExpect(jsonPath("$.data.outcome").value("FAILED"));
        flushAndClear();

        assertThat(결제조회(orderId).getStatus()).isEqualTo(PaymentStatus.FAILED);
        // 승인 중인 결제가 없어져 같은 예매로 새 주문을 만들 수 있다
        주문요청(예매조회(orderId).getId()).andExpect(status().isCreated());
    }

    @Test
    void 토스에서_아직_승인될_수_있으면_승인_중으로_남긴다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(3));
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(
                new TossPayment(PAYMENT_KEY, orderId, "IN_PROGRESS", null,
                        14000, 14000, null, List.of())));

        정리요청(orderId).andExpect(jsonPath("$.data.outcome").value("UNRESOLVED"));
        flushAndClear();

        assertThat(결제조회(orderId).getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    void 스케줄러가_오래된_결과_불명_결제를_찾아_정리한다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(2));
        토스조회결과_승인(orderId);

        recoveryService.recoverStale();
        flushAndClear();

        assertThat(예매조회(orderId).getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void 회원이_요청한_취소의_결과를_모르면_스케줄러가_다시_보내_마친다() throws Exception {
        String orderId = 결제완료();
        Long id = 예매조회(orderId).getId();
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));
        mockMvc.perform(delete("/api/reservations/{id}", id).with(bearer(user)))
                .andExpect(status().isGatewayTimeout());
        flushAndClear();

        setNow(NOW.plusMinutes(3));
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willAnswer(TestFixtures.tossCancelsFrom(14000));
        recoveryService.recoverStale();
        flushAndClear();

        assertThat(예매조회(orderId).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(결제조회(orderId).getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void 회원은_정리_API를_부를_수_없다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(3));

        mockMvc.perform(post("/api/admin/reservations/payments/{orderId}/recovery", orderId).with(bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 우리_예매_주문이_아니면_404() throws Exception {
        정리요청("cgv-r-ffffffffffffffffffffffffffffffff")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void setNow(LocalDateTime now) {
        given(clock.instant()).willReturn(now.atZone(ZONE).toInstant());
        given(clock.getZone()).willReturn(ZONE);
    }

    // A1 선점 → 주문 → 승인 응답이 끊김(504). 결제는 paymentKey를 가진 채 승인 중으로 남는다
    private String 결과불명결제() throws Exception {
        Long id = 선점();
        String orderId = 주문(id);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willReturn(new Unknown("Read timed out"));
        승인요청(id, orderId).andExpect(status().isGatewayTimeout());
        flushAndClear();
        return orderId;
    }

    private String 결제완료() throws Exception {
        Long id = 선점();
        String orderId = 주문(id);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv ->
                new Approved(TestFixtures.approvedToss(
                        inv.getArgument(0), inv.getArgument(1), inv.<Integer>getArgument(2), OffsetDateTime.now())));
        승인요청(id, orderId).andExpect(status().isOk());
        flushAndClear();
        return orderId;
    }

    private void 토스조회결과_승인(String orderId) {
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY))
                .willReturn(new Found(TestFixtures.approvedToss(PAYMENT_KEY, orderId, 14000, OffsetDateTime.now())));
    }

    private ResultActions 정리요청(String orderId) throws Exception {
        return mockMvc.perform(post("/api/admin/reservations/payments/{orderId}/recovery", orderId).with(bearer(admin)));
    }

    private Long 선점() {
        Reservation reservation = TestFixtures.hold(
                em.find(User.class, user.getId()), em.find(Screening.class, screening.getId()), LocalDateTime.now(clock));
        reservation.addSeat(1, 1, ADULT);
        persist(reservation);
        flushAndClear();
        return reservation.getId();
    }

    private ResultActions 선점요청(User requester) throws Exception {
        return mockMvc.perform(post("/api/reservations").with(bearer(requester))
                .contentType("application/json")
                .content("{\"screeningId\":%d,\"seats\":[{\"rowNum\":1,\"colNum\":1,\"audienceType\":\"ADULT\"}]}"
                        .formatted(screening.getId())));
    }

    private ResultActions 주문요청(Long id) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/payments", id).with(bearer(user)));
    }

    private String 주문(Long id) throws Exception {
        String body = 주문요청(id).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.orderId");
    }

    private ResultActions 승인요청(Long id, String orderId) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/payments/confirm", id).with(bearer(user))
                .contentType("application/json")
                .content("{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":14000}".formatted(PAYMENT_KEY, orderId)));
    }

    private Payment 결제조회(String orderId) {
        return em.createQuery("select p from Payment p where p.orderId = :orderId", Payment.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    private Reservation 예매조회(String orderId) {
        return 결제조회(orderId).getReservation();
    }

    private PaymentCancel 취소요청(String orderId) {
        return em.createQuery("select c from PaymentCancel c where c.payment.orderId = :orderId", PaymentCancel.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }
}
