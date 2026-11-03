package com.ceos.cgv.domain.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ceos.cgv.domain.payment.dto.request.*;
import com.ceos.cgv.domain.payment.dto.response.*;
import com.ceos.cgv.domain.payment.enums.*;
import com.ceos.cgv.domain.payment.gateway.*;
import com.ceos.cgv.domain.payment.infrastructure.toss.TossPaymentGateway;
import com.ceos.cgv.domain.payment.infrastructure.toss.config.TossPaymentProperties;
import com.ceos.cgv.domain.reservation.dto.*;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldService;
import com.ceos.cgv.domain.reservation.service.payment.ReservationPaymentService;
import com.ceos.cgv.global.exception.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentWorkflowIntegrationTest extends com.ceos.cgv.support.DatabaseIntegrationTest {
    @Autowired PaymentWorkflowService service;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.ceos.cgv.global.security.jwt.JwtService jwt;
    @Autowired PaymentQueryService queries;
    @Autowired SeatHoldService holds;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean PaymentGateway gateway;
    @MockitoSpyBean ReservationPaymentService reservationPayments;

    @BeforeEach
    void setUp() {
        jdbc.update(
                "INSERT INTO users (user_id,name,email) VALUES"
                    + " (8711,'회원','payment@example.com'),(8718,'타인','payment-other@example.com')");
        jdbc.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (8712,'영화관','서울')");
        jdbc.update(
                "INSERT INTO screens (screen_id,cinema_id,screen_type,row_count,seats_per_row)"
                        + " VALUES (8713,8712,'GENERAL',1,3)");
        jdbc.update(
                "INSERT INTO movies"
                    + " (movie_id,title,description,running_time,release_date,age_rating,visibility)"
                    + " VALUES (8714,'영화','설명',120,'2026-09-15','ALL','PUBLIC')");
        jdbc.update(
                "INSERT INTO screenings (screening_id,movie_id,screen_id,start_at) VALUES"
                        + " (8715,8714,8713,'2026-09-26 12:30:00')");
        jdbc.update(
                "INSERT INTO screening_seats (screening_seat_id,screening_id,seat_row,seat_number)"
                        + " VALUES (8716,8715,'A',1),(8717,8715,'A',2),(8719,8715,'A',3)");
    }

    @Autowired jakarta.persistence.EntityManager entityManager;

    void expireFixture(Long reservationId) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    com.ceos.cgv.domain.reservation.entity.Reservation reservation = entityManager.find(
                            com.ceos.cgv.domain.reservation.entity.Reservation.class, reservationId);
                    org.springframework.test.util.ReflectionTestUtils.setField(
                            reservation, "expiresAt", Instant.now().minusSeconds(60));
                });
    }

    // verifyReady는 설정만 확인하므로, PG 요청이 없었는지는 요청 메서드로 구분한다.
    void verifyNoGatewayRequests() {
        verify(gateway, never()).confirm(any());
        verify(gateway, never()).getPayment(any());
        verify(gateway, never()).cancel(any());
    }

    void setOrderExpiry(Long paymentId, Instant expiresAt) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                org.springframework.test.util.ReflectionTestUtils.setField(
                                        // LAZY 프록시가 아니라 실제 엔티티의 필드를 바꾼다.
                                        org.hibernate.Hibernate.unproxy(
                                                entityManager
                                                        .find(
                                                                com.ceos.cgv.domain.payment.entity
                                                                        .Payment.class,
                                                                paymentId)
                                                        .getOrder()),
                                        "expiresAt",
                                        expiresAt));
    }

    PaymentOrderResponse order(int count) {
        List<ReservedSeatRequest> seats = new ArrayList<>();
        for (int i = 1; i <= count; i++) seats.add(new ReservedSeatRequest("A", i));
        long id =
                holds.create(8711L, UUID.randomUUID(), new SeatHoldCreateRequest(8715L, seats))
                        .response()
                        .reservationId();
        return service.createOrder(8711L, new PaymentOrderCreateRequest(id));
    }

    GatewayPayment done(PaymentOrderResponse order) {
        return GatewayPayment.builder()
                .paymentKey("pay-" + order.orderId())
                .orderId(order.orderId())
                .totalAmount(order.totalAmount())
                .balanceAmount(order.totalAmount())
                .status(GatewayPaymentStatus.DONE)
                .approvedAt(Instant.now())
                .method("카드")
                .build();
    }

    GatewayPayment cancellationResult(
            GatewayPayment payment,
            long balance,
            GatewayPaymentStatus status,
            List<GatewayCancellation> cancellations) {
        return new GatewayPayment(
                payment.paymentKey(),
                payment.orderId(),
                payment.totalAmount(),
                balance,
                status,
                payment.method(),
                payment.approvedAt(),
                payment.receiptUrl(),
                cancellations);
    }

    PaymentOperationResponse confirm(PaymentOrderResponse order) {
        GatewayPayment payment = done(order);
        when(gateway.confirm(any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return payment;
                        });
        return service.confirm(
                8711L,
                UUID.randomUUID(),
                new PaymentConfirmRequest(
                        payment.paymentKey(), order.orderId(), order.totalAmount()));
    }

    @Test
    void 주문은_선점당_하나이며_승인은_좌석을_확정한다() {
        PaymentOrderResponse order = order(2);
        assertThat(
                        service.createOrder(
                                        8711L, new PaymentOrderCreateRequest(order.reservationId()))
                                .paymentId())
                .isEqualTo(order.paymentId());
        PaymentOperationResponse result = confirm(order);
        assertThat(result.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(result.result().status()).isEqualTo(PaymentStatus.DONE);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RESERVED");
        verify(gateway, times(1)).confirm(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("orderNames")
    void 주문명은_좌석문구와_Unicode_문자를_보존하며_SDK_100자_한도를_지킨다(
            String movieTitle, String expectedOrderName) {
        jdbc.update("UPDATE movies SET title=? WHERE movie_id=8714", movieTitle);

        PaymentOrderResponse order = order(2);

        assertThat(order.orderName()).isEqualTo(expectedOrderName);
        assertThat(order.orderName().length()).isLessThanOrEqualTo(100);
        assertThat(queries.order(8711L, order.orderId()).orderName()).isEqualTo(expectedOrderName);
        verifyNoInteractions(gateway);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> orderNames() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("영화", "영화 2석"),
                org.junit.jupiter.params.provider.Arguments.of("가".repeat(200), "가".repeat(97) + " 2석"),
                org.junit.jupiter.params.provider.Arguments.of(
                        "가".repeat(96) + "🎬" + "나".repeat(102), "가".repeat(96) + " 2석"),
                org.junit.jupiter.params.provider.Arguments.of(
                        "가".repeat(95) + "🎬" + "나".repeat(103), "가".repeat(95) + "🎬 2석"));
    }

    @Test
    void 금액조작과_타인_주문은_PG_호출_전에_거절한다() {
        PaymentOrderResponse order = order(1);
        assertThatThrownBy(
                        () ->
                                service.confirm(
                                        8711L,
                                        UUID.randomUUID(),
                                        new PaymentConfirmRequest("key", order.orderId(), 1L)))
                .hasMessageContaining("금액");
        assertThatThrownBy(() -> queries.order(8718L, order.orderId())).hasMessageContaining("권한");
        verifyNoGatewayRequests();
    }

    @Test
    void 승인_타임아웃은_선점을_보존하고_조회로_복구한다() {
        PaymentOrderResponse order = order(1);
        UUID key = UUID.randomUUID();
        GatewayPayment payment = done(order);
        when(gateway.confirm(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        PaymentOperationResponse pending =
                service.confirm(
                        8711L,
                        key,
                        new PaymentConfirmRequest(
                                payment.paymentKey(), order.orderId(), order.totalAmount()));
        assertThat(pending.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId())
                .isEqualTo(pending.operationId());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("PAYMENT_PENDING");
        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(List.of(999999L), "범위밖")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SEAT);
        when(gateway.getPayment(payment.paymentKey())).thenReturn(payment);
        assertThat(service.reconcile(8711L, pending.operationId()).status())
                .isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        verify(gateway, times(1)).confirm(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = GatewayPaymentStatus.class,
            names = {"CANCELED", "PARTIAL_CANCELED"})
    void 부분취소_후_전체취소와_이전결과_재전송은_누적잔액을_보존한다(
            GatewayPaymentStatus finalStatus) {
        PaymentOrderResponse order = order(2);
        PaymentOperationResponse approved = confirm(order);
        GatewayPayment payment = done(order);
        UUID partKey = UUID.randomUUID(), fullKey = UUID.randomUUID();
        List<GatewayCancellation> cancels = new ArrayList<>();
        when(gateway.cancel(any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            CancelPaymentCommand command = call.getArgument(0);
                            cancels.add(
                                    new GatewayCancellation(
                                            UUID.randomUUID().toString(),
                                            command.cancelAmount(),
                                            Instant.now(),
                                            "DONE"));
                            long balance = command.refundableAmount() - command.cancelAmount();
                            return new GatewayPayment(
                                    payment.paymentKey(),
                                    payment.orderId(),
                                    payment.totalAmount(),
                                    balance,
                                    balance == 0
                                            ? finalStatus
                                            : GatewayPaymentStatus.PARTIAL_CANCELED,
                                    "카드",
                                    payment.approvedAt(),
                                    null,
                                    List.copyOf(cancels));
                        });
        PaymentCancelRequest part =
                new PaymentCancelRequest(
                        List.of(order.seats().getFirst().reservedSeatId()), "좌석 취소");
        PaymentOperationResponse partial = service.cancel(8711L, order.paymentId(), partKey, part);
        assertThat(partial.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(partial.result().status()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
        assertThat(partial.result().balanceAmount()).isEqualTo(10000);
        PaymentOperationResponse full =
                service.cancel(
                        8711L, order.paymentId(), fullKey, new PaymentCancelRequest(null, "전체 취소"));
        assertThat(full.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(full.result().status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(full.result().balanceAmount()).isZero();
        assertThat(full.result().cancellations())
                .hasSize(2)
                .containsAll(partial.result().cancellations());
        assertThat(full.result().cancellations().stream().mapToLong(GatewayCancellation::amount).sum())
                .isEqualTo(20000);
        assertThat(full.result().seats()).allMatch(seat -> seat.canceledAt() != null);
        assertThat(queries.payment(8711L, order.paymentId()).status())
                .isEqualTo(PaymentStatus.CANCELED);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM reservations WHERE reservation_id=?",
                String.class, order.reservationId())).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?",
                Integer.class, order.reservationId())).isZero();
        assertThat(
                        service.cancel(
                                8711L,
                                order.paymentId(),
                                fullKey,
                                new PaymentCancelRequest(null, "전체 취소")))
                .isEqualTo(full);
        assertThat(service.cancel(8711L, order.paymentId(), partKey, part)).isEqualTo(partial);
        assertThat(service.reconcile(8711L, full.operationId())).isEqualTo(full);
        assertThat(queries.operation(8711L, approved.operationId()).result().status())
                .isEqualTo(PaymentStatus.DONE);
        verify(gateway, times(2)).cancel(any());
        verify(gateway, never()).getPayment(any());
    }

    @Test
    void 누적취소_UNKNOWN은_이력검증_후_잔액0_부분취소상태를_조회로만_복구한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        Instant canceledAt = Instant.parse("2026-10-05T00:00:00Z");
        GatewayCancellation first = new GatewayCancellation("first", 10000, canceledAt, "DONE");
        GatewayCancellation last = new GatewayCancellation("last", 10000, canceledAt, "DONE");
        List<GatewayCancellation> completeHistory = List.of(first, last);
        when(gateway.cancel(any())).thenReturn(
                cancellationResult(payment, 10000, GatewayPaymentStatus.PARTIAL_CANCELED, List.of(first)),
                cancellationResult(payment, 0, GatewayPaymentStatus.DONE, completeHistory));
        PaymentOperationResponse partial = service.cancel(
                8711L, order.paymentId(), UUID.randomUUID(),
                new PaymentCancelRequest(List.of(order.seats().getFirst().reservedSeatId()), "첫좌석"));
        assertThat(partial.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        UUID finalKey = UUID.randomUUID();
        PaymentCancelRequest remaining = new PaymentCancelRequest(null, "남은전체");
        PaymentOperationResponse pending = service.cancel(8711L, order.paymentId(), finalKey, remaining);
        assertThat(pending.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(pending.failureCode()).isEqualTo("INVALID_CANCELLATION_RESULT");
        clearInvocations(gateway);

        List<GatewayPayment> invalidResults = List.of(
                cancellationResult(payment, 10000, GatewayPaymentStatus.PARTIAL_CANCELED, completeHistory),
                cancellationResult(payment, 0, GatewayPaymentStatus.DONE, completeHistory),
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED, List.of(first)),
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED,
                        List.of(first, new GatewayCancellation("last", 9999, canceledAt, "DONE"))),
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED, List.of(first, first)),
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED,
                        List.of(first, new GatewayCancellation(null, 10000, canceledAt, "DONE"))),
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED,
                        List.of(first, last, new GatewayCancellation("zero", 0, canceledAt, "DONE"))),
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED,
                        List.of(first,
                                new GatewayCancellation("last", 11000, canceledAt, "DONE"),
                                new GatewayCancellation("negative", -1000, canceledAt, "DONE"))),
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED,
                        List.of(first, new GatewayCancellation("last", 10000, canceledAt, "FAILED"))),
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED,
                        List.of(first, new GatewayCancellation("last", 10000, null, "DONE"))),
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED,
                        List.of(new GatewayCancellation("first", 10000, canceledAt.plusSeconds(1), "DONE"), last)),
                new GatewayPayment("wrong-key", payment.orderId(), 20000, 0,
                        GatewayPaymentStatus.PARTIAL_CANCELED, "카드", payment.approvedAt(), null, completeHistory),
                new GatewayPayment(payment.paymentKey(), "wrong-order", 20000, 0,
                        GatewayPaymentStatus.PARTIAL_CANCELED, "카드", payment.approvedAt(), null, completeHistory),
                new GatewayPayment(payment.paymentKey(), payment.orderId(), 30000, 0,
                        GatewayPaymentStatus.PARTIAL_CANCELED, "카드", payment.approvedAt(), null, completeHistory));
        for (GatewayPayment invalid : invalidResults) {
            when(gateway.getPayment(payment.paymentKey())).thenReturn(invalid);
            PaymentOperationResponse unknown = service.reconcile(8711L, pending.operationId());
            assertThat(unknown.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
            assertThat(unknown.failureCode()).isEqualTo("INVALID_CANCELLATION_RESULT");
            PaymentResponse unchanged = queries.payment(8711L, order.paymentId());
            assertThat(unchanged.status()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
            assertThat(unchanged.balanceAmount()).isEqualTo(10000);
            assertThat(unchanged.cancellations()).containsExactly(first);
            assertThat(unchanged.activeOperationId()).isEqualTo(pending.operationId());
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?",
                    Integer.class, order.reservationId())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM reservations WHERE reservation_id=?",
                    String.class, order.reservationId())).isEqualTo("RESERVED");
        }

        when(gateway.getPayment(payment.paymentKey())).thenReturn(
                cancellationResult(payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED, completeHistory));
        PaymentOperationResponse completed = service.reconcile(8711L, pending.operationId());
        assertThat(completed.operationId()).isEqualTo(pending.operationId());
        assertThat(completed.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(completed.result().status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(completed.result().balanceAmount()).isZero();
        assertThat(completed.result().cancellations()).containsExactly(first, last);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM reservations WHERE reservation_id=?",
                String.class, order.reservationId())).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?",
                Integer.class, order.reservationId())).isZero();
        assertThat(service.cancel(8711L, order.paymentId(), finalKey, remaining)).isEqualTo(completed);
        assertThat(service.reconcile(8711L, pending.operationId())).isEqualTo(completed);
        verify(gateway, never()).cancel(any());
        verify(gateway, times(invalidResults.size() + 1)).getPayment(payment.paymentKey());
    }

    @Test
    void 동일키_다른내용은_충돌하고_다른키_동일승인은_PG를_다시_호출하지_않는다() {
        PaymentOrderResponse order = order(1);
        UUID key = UUID.randomUUID();
        GatewayPayment payment = done(order);
        when(gateway.confirm(any())).thenReturn(payment);
        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        payment.paymentKey(), order.orderId(), order.totalAmount());
        PaymentOperationResponse approved = service.confirm(8711L, key, request);
        assertThat(service.confirm(8711L, key, request)).isEqualTo(approved);
        assertThat(service.confirm(8711L, UUID.randomUUID(), request).status())
                .isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThatThrownBy(
                        () ->
                                service.confirm(
                                        8711L,
                                        key,
                                        new PaymentConfirmRequest(
                                                payment.paymentKey(), order.orderId(), 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_REQUEST_CONFLICT);
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 만료_주문은_PG_호출_없이_거절한다() {
        PaymentOrderResponse order = order(1);
        expireFixture(order.reservationId());
        assertThatThrownBy(
                        () ->
                                service.confirm(
                                        8711L,
                                        UUID.randomUUID(),
                                        new PaymentConfirmRequest(
                                                "key", order.orderId(), order.totalAmount())))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.HOLD_EXPIRED);
        verifyNoGatewayRequests();
    }

    @Test
    void 승인_확정거절은_선점을_해제하고_같은키로_재승인하지_않는다() {
        PaymentOrderResponse order = order(1);
        UUID key = UUID.randomUUID();
        PaymentConfirmRequest request =
                new PaymentConfirmRequest("key", order.orderId(), order.totalAmount());
        when(gateway.confirm(any()))
                .thenThrow(
                        new PaymentGatewayException(
                                GatewayFailureType.REJECTED, "INVALID_PAYMENT"));
        PaymentOperationResponse failed = service.confirm(8711L, key, request);
        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RELEASED");
        assertThat(service.confirm(8711L, key, request)).isEqualTo(failed);
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void PG_성공후_DB확정_일시실패는_조회로_같은작업을_완료한다() {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                doThrow(
                                                new DataAccessResourceFailureException(
                                                        "simulated database outage"))
                                        .doCallRealMethod()
                                        .when(reservationPayments)
                                        .confirmPayment(order.reservationId(), 8711L));
        PaymentOperationResponse unknown = confirm(order);
        assertThat(unknown.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("PAYMENT_PENDING");
        when(gateway.getPayment(payment.paymentKey())).thenReturn(payment);
        assertThat(service.reconcile(8711L, unknown.operationId()).status())
                .isEqualTo(PaymentOperationStatus.SUCCEEDED);
        verify(gateway, times(1)).confirm(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = GatewayPaymentStatus.class,
            names = {"CANCELED", "PARTIAL_CANCELED"})
    void PG_성공후_영구좌석확정실패는_고정키_보상취소_후에만_해제한다(
            GatewayPaymentStatus cancellationStatus) {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                doThrow(new BusinessException(ErrorCode.SEAT_OWNER_MISMATCH))
                                        .when(reservationPayments)
                                        .confirmPayment(order.reservationId(), 8711L));
        when(gateway.cancel(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        PaymentOperationResponse compensation = confirm(order);
        assertThat(compensation.status()).isEqualTo(PaymentOperationStatus.COMPENSATING);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("PAYMENT_PENDING");
        when(gateway.getPayment(payment.paymentKey())).thenReturn(payment);
        GatewayPayment canceled =
                new GatewayPayment(
                        payment.paymentKey(),
                        payment.orderId(),
                        payment.totalAmount(),
                        0,
                        cancellationStatus,
                        "카드",
                        payment.approvedAt(),
                        null,
                        List.of(
                                new GatewayCancellation(
                                        "compensation",
                                        payment.totalAmount(),
                                        Instant.now(),
                                        "DONE")));
        doReturn(canceled).when(gateway).cancel(any());
        PaymentOperationResponse recovered = service.reconcile(8711L, compensation.operationId());
        assertThat(recovered.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(recovered.result().status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RELEASED");
        org.mockito.ArgumentCaptor<CancelPaymentCommand> commands =
                org.mockito.ArgumentCaptor.forClass(CancelPaymentCommand.class);
        verify(gateway, times(2)).cancel(commands.capture());
        assertThat(commands.getAllValues())
                .extracting(CancelPaymentCommand::idempotencyKey)
                .containsOnly(commands.getValue().idempotencyKey());
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 보상취소중_조회가_잔액0_부분취소상태를_증명하면_추가취소없이_해제한다() {
        PaymentOrderResponse order = order(2);
        GatewayPayment payment = done(order);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                doThrow(new BusinessException(ErrorCode.SEAT_OWNER_MISMATCH))
                                        .when(reservationPayments)
                                        .confirmPayment(order.reservationId(), 8711L));
        when(gateway.cancel(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        PaymentOperationResponse pending = confirm(order);
        assertThat(pending.status()).isEqualTo(PaymentOperationStatus.COMPENSATING);
        GatewayPayment canceled = cancellationResult(
                payment, 0, GatewayPaymentStatus.PARTIAL_CANCELED,
                List.of(
                        new GatewayCancellation("compensation-first", 10000, Instant.now(), "DONE"),
                        new GatewayCancellation("compensation-last", 10000, Instant.now(), "DONE")));
        when(gateway.getPayment(payment.paymentKey())).thenReturn(canceled);
        clearInvocations(gateway);

        PaymentOperationResponse failed = service.reconcile(8711L, pending.operationId());
        assertThat(failed.operationId()).isEqualTo(pending.operationId());
        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("APPROVAL_COMPENSATED");
        assertThat(failed.result().status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(failed.result().balanceAmount()).isZero();
        assertThat(failed.result().cancellations())
                .containsExactlyElementsOf(canceled.cancellations());
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM reservations WHERE reservation_id=?",
                String.class, order.reservationId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?",
                Integer.class, order.reservationId())).isZero();
        assertThat(service.reconcile(8711L, pending.operationId())).isEqualTo(failed);
        verify(gateway, times(1)).getPayment(payment.paymentKey());
        verify(gateway, never()).cancel(any());
    }

    @Test
    void 잘못된_승인응답과_NOT_FOUND조회는_불명확한_작업을_해제하지_않는다() {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        when(gateway.confirm(any()))
                .thenReturn(
                        new GatewayPayment(
                                "wrong-key",
                                payment.orderId(),
                                payment.totalAmount(),
                                payment.totalAmount(),
                                GatewayPaymentStatus.DONE,
                                "카드",
                                Instant.now(),
                                null,
                                List.of()));
        PaymentOperationResponse unknown =
                service.confirm(
                        8711L,
                        UUID.randomUUID(),
                        new PaymentConfirmRequest(
                                payment.paymentKey(), order.orderId(), order.totalAmount()));
        assertThat(unknown.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        when(gateway.getPayment(payment.paymentKey()))
                .thenThrow(
                        new PaymentGatewayException(
                                GatewayFailureType.NOT_FOUND, "NOT_FOUND_PAYMENT"));
        assertThat(service.reconcile(8711L, unknown.operationId()).status())
                .isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8716",
                                Long.class))
                .isEqualTo(order.reservationId());
    }

    @Test
    void 승인_진행중_다른키의_동시요청은_기존작업을_반환한다() throws Exception {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        when(gateway.confirm(any()))
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
                            return payment;
                        });
        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        payment.paymentKey(), order.orderId(), order.totalAmount());
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<PaymentOperationResponse> first =
                    executor.submit(() -> service.confirm(8711L, UUID.randomUUID(), request));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(
                            () ->
                                    service.confirm(
                                            8711L,
                                            UUID.randomUUID(),
                                            new PaymentConfirmRequest(
                                                    payment.paymentKey(), order.orderId(), 1L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            assertThatThrownBy(
                            () ->
                                    service.confirm(
                                            8711L,
                                            UUID.randomUUID(),
                                            new PaymentConfirmRequest(
                                                    "wrong", order.orderId(), order.totalAmount())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_REQUEST_CONFLICT);
            PaymentOperationResponse busy = service.confirm(8711L, UUID.randomUUID(), request);
            assertThat(busy.status()).isEqualTo(PaymentOperationStatus.PROCESSING);
            finish.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).operationId()).isEqualTo(busy.operationId());
        } finally {
            finish.countDown();
        }
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 취소거절과_잘못된좌석은_기존잔액과_점유를_보존한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(
                                                List.of(
                                                        order.seats().getFirst().reservedSeatId(),
                                                        order.seats().getFirst().reservedSeatId()),
                                                "중복")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(List.of(999999L), "타좌석")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(List.of(), "빈좌석")))
                .isInstanceOf(BusinessException.class);
        when(gateway.cancel(any()))
                .thenThrow(
                        new PaymentGatewayException(
                                GatewayFailureType.REJECTED, "CANCEL_REJECTED"));
        PaymentOperationResponse failed =
                service.cancel(
                        8711L,
                        order.paymentId(),
                        UUID.randomUUID(),
                        new PaymentCancelRequest(null, "전체"));
        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(queries.payment(8711L, order.paymentId()).balanceAmount()).isEqualTo(20000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM screening_seats WHERE"
                                        + " current_reservation_id=?",
                                Integer.class,
                                order.reservationId()))
                .isEqualTo(2);
        verify(gateway, times(1)).cancel(any());
    }

    @Test
    void 공식_할인_부분취소_거절_후_새키로_전체취소할_수_있다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        String baseUrl = "https://api.tosspayments.com";
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossPaymentGateway adapter = new TossPaymentGateway(builder.build(),
                new TossPaymentProperties("test_ck_fixture", "test_sk_fixture", null, null));
        String cancelUrl = baseUrl + "/v1/payments/" + payment.paymentKey() + "/cancel";
        // 공식 HTTP 모의 응답 계약이며 실제 PG 할인 거래의 검증은 아니다.
        server.expect(requestTo(cancelUrl)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"cancelReason":"부분 취소","cancelAmount":10000,"refundableAmount":20000}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT","message":"fixture rejection"}
                                """));
        server.expect(requestTo(cancelUrl)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"cancelReason":"전체 취소","cancelAmount":20000,"refundableAmount":20000}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"paymentKey":"%s","orderId":"%s","totalAmount":20000,
                         "balanceAmount":0,"status":"CANCELED","method":"카드",
                         "approvedAt":"2026-10-05T12:00:00+09:00","receipt":null,
                         "cancels":[{"transactionKey":"full-cancel","cancelAmount":20000,
                         "canceledAt":"2026-10-05T13:00:00+09:00","cancelStatus":"DONE"}]}
                        """.formatted(payment.paymentKey(), payment.orderId()), MediaType.APPLICATION_JSON));
        when(gateway.cancel(any())).thenAnswer(call -> adapter.cancel(call.getArgument(0)));
        when(gateway.getPayment(payment.paymentKey())).thenReturn(payment);
        UUID partialKey = UUID.randomUUID();
        PaymentCancelRequest partialRequest = new PaymentCancelRequest(
                List.of(order.seats().getFirst().reservedSeatId()), "부분 취소");

        PaymentOperationResponse partial =
                service.cancel(8711L, order.paymentId(), partialKey, partialRequest);

        assertThat(partial.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(partial.failureCode()).isEqualTo("EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT");
        assertThat(partial.result().balanceAmount()).isEqualTo(20000);
        assertThat(partial.result().status()).isEqualTo(PaymentStatus.DONE);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?",
                Integer.class, order.reservationId())).isEqualTo(2);
        assertThat(service.cancel(8711L, order.paymentId(), partialKey, partialRequest)).isEqualTo(partial);
        assertThat(service.reconcile(8711L, partial.operationId())).isEqualTo(partial);

        PaymentOperationResponse full = service.cancel(8711L, order.paymentId(), UUID.randomUUID(),
                new PaymentCancelRequest(null, "전체 취소"));

        assertThat(full.operationId()).isNotEqualTo(partial.operationId());
        assertThat(full.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(full.result().status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(full.result().balanceAmount()).isZero();
        assertThat(full.result().seats()).allMatch(seat -> seat.canceledAt() != null);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?",
                Integer.class, order.reservationId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM reservations WHERE reservation_id=?",
                String.class, order.reservationId())).isEqualTo("CANCELED");
        verify(gateway, times(2)).cancel(any());
        verify(gateway, never()).getPayment(any());
        server.verify();
    }

    @Test
    void 일반_결제조회와_작업조회는_PG를_호출하지_않는다() {
        PaymentOrderResponse order = order(1);
        PaymentOperationResponse approved = confirm(order);
        clearInvocations(gateway);
        assertThat(queries.payment(8711L, order.paymentId()).status())
                .isEqualTo(PaymentStatus.DONE);
        assertThat(queries.operation(8711L, approved.operationId()).status())
                .isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThatThrownBy(() -> queries.payment(8718L, order.paymentId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.reconcile(8718L, approved.operationId()))
                .isInstanceOf(BusinessException.class);
        verifyNoGatewayRequests();
    }

    @Test
    void 숨김영화는_결제시작을_거절하고_완료재전송은_숨김과_기한변경의_영향을_받지_않는다() {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        UUID key = UUID.randomUUID();
        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        payment.paymentKey(), order.orderId(), order.totalAmount());
        jdbc.update("UPDATE movies SET visibility='HIDDEN' WHERE movie_id=8714");
        assertThatThrownBy(() -> service.confirm(8711L, key, request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.MOVIE_NOT_AVAILABLE);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("HELD");
        verifyNoGatewayRequests();
        jdbc.update("UPDATE movies SET visibility='PUBLIC' WHERE movie_id=8714");
        when(gateway.confirm(any())).thenReturn(payment);
        PaymentOperationResponse approved = service.confirm(8711L, key, request);
        jdbc.update("UPDATE movies SET visibility='HIDDEN' WHERE movie_id=8714");
        expireFixture(order.reservationId());
        assertThat(service.confirm(8711L, key, request)).isEqualTo(approved);
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 취소한_주문을_새키로_승인하지_않고_기존키는_과거승인결과를_보여준다() {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        UUID approvalKey = UUID.randomUUID();
        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        payment.paymentKey(), order.orderId(), order.totalAmount());
        when(gateway.confirm(any())).thenReturn(payment);
        PaymentOperationResponse approved = service.confirm(8711L, approvalKey, request);
        when(gateway.cancel(any()))
                .thenReturn(
                        new GatewayPayment(
                                payment.paymentKey(),
                                payment.orderId(),
                                payment.totalAmount(),
                                0,
                                GatewayPaymentStatus.CANCELED,
                                "카드",
                                payment.approvedAt(),
                                null,
                                List.of(
                                        new GatewayCancellation(
                                                "full",
                                                payment.totalAmount(),
                                                Instant.now(),
                                                "DONE"))));
        service.cancel(
                8711L, order.paymentId(), UUID.randomUUID(), new PaymentCancelRequest(null, "전체"));
        assertThatThrownBy(() -> service.confirm(8711L, UUID.randomUUID(), request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_STATE_CONFLICT);
        assertThat(service.confirm(8711L, approvalKey, request)).isEqualTo(approved);
        assertThat(queries.payment(8711L, order.paymentId()).status())
                .isEqualTo(PaymentStatus.CANCELED);
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 결제키는_다른주문에서_다시_사용할_수_없다() {
        PaymentOrderResponse first = order(1);
        confirm(first);
        long secondHold =
                holds.create(
                                8718L,
                                UUID.randomUUID(),
                                new SeatHoldCreateRequest(
                                        8715L, List.of(new ReservedSeatRequest("A", 2))))
                        .response()
                        .reservationId();
        PaymentOrderResponse second =
                service.createOrder(8718L, new PaymentOrderCreateRequest(secondHold));
        assertThatThrownBy(
                        () ->
                                service.confirm(
                                        8718L,
                                        UUID.randomUUID(),
                                        new PaymentConfirmRequest(
                                                done(first).paymentKey(),
                                                second.orderId(),
                                                second.totalAmount())))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_KEY_CONFLICT);
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 취소_타임아웃_조회는_예상잔액과_취소이력이_모두_일치할_때만_좌석을_해제한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        when(gateway.cancel(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        List<Long> selected = List.of(order.seats().getFirst().reservedSeatId());
        PaymentOperationResponse pending =
                service.cancel(
                        8711L,
                        order.paymentId(),
                        UUID.randomUUID(),
                        new PaymentCancelRequest(selected, "부분"));
        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(List.of(999999L), "범위밖")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SEAT);
        when(gateway.getPayment(payment.paymentKey())).thenReturn(payment);
        assertThat(service.reconcile(8711L, pending.operationId()).status())
                .isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(
                        service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(null, "다른취소"))
                                .operationId())
                .isEqualTo(pending.operationId());
        // A plausible balance without the cumulative transaction history is not proof of
        // cancellation.
        when(gateway.getPayment(payment.paymentKey()))
                .thenReturn(
                        new GatewayPayment(
                                payment.paymentKey(),
                                payment.orderId(),
                                20000,
                                10000,
                                GatewayPaymentStatus.PARTIAL_CANCELED,
                                "카드",
                                payment.approvedAt(),
                                null,
                                List.of()));
        assertThat(service.reconcile(8711L, pending.operationId()).status())
                .isEqualTo(PaymentOperationStatus.UNKNOWN);
        when(gateway.getPayment(payment.paymentKey())).thenReturn(
                cancellationResult(payment, 10000, GatewayPaymentStatus.CANCELED,
                        List.of(new GatewayCancellation("partial", 10000, Instant.now(), "DONE"))));
        assertThat(service.reconcile(8711L, pending.operationId()).status())
                .isEqualTo(PaymentOperationStatus.UNKNOWN);
        when(gateway.getPayment(payment.paymentKey()))
                .thenReturn(
                        new GatewayPayment(
                                payment.paymentKey(),
                                payment.orderId(),
                                20000,
                                10000,
                                GatewayPaymentStatus.PARTIAL_CANCELED,
                                "카드",
                                payment.approvedAt(),
                                null,
                                List.of(
                                        new GatewayCancellation(
                                                "partial", 10000, Instant.now(), "DONE"))));
        assertThat(service.reconcile(8711L, pending.operationId()).status())
                .isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8716",
                                Long.class))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8717",
                                Long.class))
                .isEqualTo(order.reservationId());
        assertThat(
                        holds.create(
                                        8718L,
                                        UUID.randomUUID(),
                                        new SeatHoldCreateRequest(
                                                8715L, List.of(new ReservedSeatRequest("A", 1))))
                                .created())
                .isTrue();
        // 첫 복구는 PG 상태가 취소 전 그대로여서 같은 멱등키로 한 번 재전송했다(이 응답도 TIMEOUT).
        verify(gateway, times(2)).cancel(any());
    }

    String token(long id) {
        return "Bearer " + jwt.issue(id, com.ceos.cgv.domain.user.enums.UserRole.USER);
    }

    @Test
    void HTTP_불명확결제는_202와_작업주소를_주고_유료예약_DELETE는_환불을_우회하지_않는다() throws Exception {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        when(gateway.confirm(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        String body =
                "{\"paymentKey\":\""
                        + payment.paymentKey()
                        + "\",\"orderId\":\""
                        + order.orderId()
                        + "\",\"amount\":"
                        + order.totalAmount()
                        + "}";
        String response =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/payments/confirm")
                                        .header("Authorization", token(8711L))
                                        .header("Idempotency-Key", UUID.randomUUID())
                                        .contentType(
                                                org.springframework.http.MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isAccepted())
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .jsonPath("$.status")
                                        .value(202))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .jsonPath("$.data.status")
                                        .value("UNKNOWN"))
                        .andReturn()
                        .getResponse()
                        .getHeader("Location");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        response)
                                .header("Authorization", token(8718L)))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isForbidden());
        when(gateway.getPayment(payment.paymentKey())).thenReturn(payment);
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        response + "/reconcile")
                                .header("Authorization", token(8711L)))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                        "/api/v1/reservations/" + order.reservationId())
                                .header("Authorization", token(8711L)))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isBadRequest());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RESERVED");
        when(gateway.cancel(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                        "/api/v1/reservations/" + order.reservationId())
                                .header("Authorization", token(8711L))
                                .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isAccepted());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8716",
                                Long.class))
                .isEqualTo(order.reservationId());
    }

    @Test
    void HTTP_결제_인증과_UUID_입력검증은_PG앞에서_적용한다() throws Exception {
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/payments/confirm"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isUnauthorized());
        PaymentOrderResponse order = order(1);
        String body =
                "{\"paymentKey\":\"key\",\"orderId\":\"" + order.orderId() + "\",\"amount\":10000}";
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/v1/payments/confirm")
                                .header("Authorization", token(8711L))
                                .header("Idempotency-Key", "bad-key")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isBadRequest());
        verifyNoInteractions(gateway);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = GatewayPaymentStatus.class,
            names = {"ABORTED", "EXPIRED"})
    void 조회가_확정실패를_증명하면_선점을_해제한다(GatewayPaymentStatus failureStatus) {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        when(gateway.confirm(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        PaymentOperationResponse pending =
                service.confirm(
                        8711L,
                        UUID.randomUUID(),
                        new PaymentConfirmRequest(
                                payment.paymentKey(), order.orderId(), order.totalAmount()));
        when(gateway.getPayment(payment.paymentKey()))
                .thenReturn(
                        new GatewayPayment(
                                payment.paymentKey(),
                                payment.orderId(),
                                payment.totalAmount(),
                                payment.totalAmount(),
                                failureStatus,
                                null,
                                null,
                                null,
                                List.of()));
        PaymentOperationResponse failed = service.reconcile(8711L, pending.operationId());
        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(failed.result().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RELEASED");
        verify(gateway, times(1)).confirm(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = GatewayPaymentStatus.class,
            names = {"CANCELED", "PARTIAL_CANCELED"})
    void 조회에서_승인_전체취소가_확인되면_다시_취소하지_않고_실패로_종결한다(
            GatewayPaymentStatus cancellationStatus) {
        PaymentOrderResponse order = order(2);
        GatewayPayment payment = done(order);
        when(gateway.confirm(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        PaymentOperationResponse pending =
                service.confirm(
                        8711L,
                        UUID.randomUUID(),
                        new PaymentConfirmRequest(
                                payment.paymentKey(), order.orderId(), order.totalAmount()));
        when(gateway.getPayment(payment.paymentKey()))
                .thenReturn(
                        new GatewayPayment(
                                payment.paymentKey(),
                                payment.orderId(),
                                payment.totalAmount(),
                                0,
                                cancellationStatus,
                                "카드",
                                payment.approvedAt(),
                                null,
                                List.of(
                                        new GatewayCancellation(
                                                "external",
                                                10000,
                                                Instant.now(),
                                                "DONE"),
                                        new GatewayCancellation(
                                                "external-last",
                                                10000,
                                                Instant.now(),
                                                "DONE"))));
        PaymentOperationResponse failed = service.reconcile(8711L, pending.operationId());
        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("APPROVAL_EXTERNALLY_CANCELED");
        assertThat(failed.result().status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(failed.result().balanceAmount()).isZero();
        assertThat(failed.result().cancellations()).hasSize(2);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?",
                Integer.class, order.reservationId())).isZero();
        assertThat(service.reconcile(8711L, pending.operationId())).isEqualTo(failed);
        verify(gateway, times(1)).getPayment(payment.paymentKey());
        verify(gateway, never()).cancel(any());
    }

    @Test
    void 취소_PG성공후_DB일시실패는_동일작업_조회로만_확정한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        List<Long> ids = List.of(order.seats().getFirst().reservedSeatId());
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                doThrow(
                                                new DataAccessResourceFailureException(
                                                        "simulated cancellation save outage"))
                                        .doCallRealMethod()
                                        .when(reservationPayments)
                                        .cancelPaidSeats(
                                                eq(order.reservationId()),
                                                eq(8711L),
                                                eq(ids),
                                                any(Instant.class)));
        GatewayPayment partial =
                new GatewayPayment(
                        payment.paymentKey(),
                        payment.orderId(),
                        20000,
                        10000,
                        GatewayPaymentStatus.PARTIAL_CANCELED,
                        "카드",
                        payment.approvedAt(),
                        null,
                        List.of(new GatewayCancellation("partial", 10000, Instant.now(), "DONE")));
        when(gateway.cancel(any())).thenReturn(partial);
        PaymentOperationResponse pending =
                service.cancel(
                        8711L,
                        order.paymentId(),
                        UUID.randomUUID(),
                        new PaymentCancelRequest(ids, "부분"));
        assertThat(pending.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId())
                .isEqualTo(pending.operationId());
        assertThat(queries.payment(8711L, order.paymentId()).balanceAmount()).isEqualTo(20000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8716",
                                Long.class))
                .isEqualTo(order.reservationId());
        when(gateway.getPayment(payment.paymentKey())).thenReturn(partial);
        PaymentOperationResponse completed = service.reconcile(8711L, pending.operationId());
        assertThat(completed.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(completed.result().balanceAmount()).isEqualTo(10000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8716",
                                Long.class))
                .isNull();
        assertThat(service.reconcile(8711L, pending.operationId())).isEqualTo(completed);
        verify(gateway, times(1)).cancel(any());
        verify(gateway, times(1)).getPayment(any());
    }

    @Test
    void 여러번의_부분취소는_단가로_누적되고_다른내용의_동일키는_거절한다() {
        PaymentOrderResponse order = order(3);
        confirm(order);
        GatewayPayment payment = done(order);
        List<GatewayCancellation> cancellations = new ArrayList<>();
        UUID firstKey = UUID.randomUUID();
        when(gateway.cancel(any()))
                .thenAnswer(
                        call -> {
                            CancelPaymentCommand command = call.getArgument(0);
                            assertThat(command.cancelAmount()).isEqualTo(10000);
                            cancellations.add(
                                    new GatewayCancellation(
                                            UUID.randomUUID().toString(),
                                            command.cancelAmount(),
                                            Instant.now(),
                                            "DONE"));
                            long balance = command.refundableAmount() - command.cancelAmount();
                            return new GatewayPayment(
                                    payment.paymentKey(),
                                    payment.orderId(),
                                    30000,
                                    balance,
                                    balance == 0
                                            ? GatewayPaymentStatus.CANCELED
                                            : GatewayPaymentStatus.PARTIAL_CANCELED,
                                    "카드",
                                    payment.approvedAt(),
                                    null,
                                    List.copyOf(cancellations));
                        });
        PaymentCancelRequest first =
                new PaymentCancelRequest(List.of(order.seats().get(0).reservedSeatId()), "첫좌석");
        PaymentOperationResponse firstResult =
                service.cancel(8711L, order.paymentId(), firstKey, first);
        assertThat(firstResult.result().balanceAmount()).isEqualTo(20000);
        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        firstKey,
                                        new PaymentCancelRequest(first.reservedSeatIds(), "다른사유")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_REQUEST_CONFLICT);
        assertThat(
                        service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(
                                                List.of(order.seats().get(1).reservedSeatId()),
                                                "두번째"))
                                .result()
                                .balanceAmount())
                .isEqualTo(10000);
        assertThat(
                        service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(null, "남은전체"))
                                .result()
                                .balanceAmount())
                .isZero();
        assertThat(service.cancel(8711L, order.paymentId(), firstKey, first))
                .isEqualTo(firstResult);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("CANCELED");
        verify(gateway, times(3)).cancel(any());
    }

    // ---- 취소 UNKNOWN 재전송 ----

    private PaymentOperationResponse pendingCancellation(
            PaymentOrderResponse order, PaymentCancelRequest request) {
        PaymentOperationResponse pending =
                service.cancel(8711L, order.paymentId(), UUID.randomUUID(), request);
        assertThat(pending.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId())
                .isEqualTo(pending.operationId());
        return pending;
    }

    private GatewayPayment partialCanceled(GatewayPayment payment) {
        return cancellationResult(
                payment,
                10000,
                GatewayPaymentStatus.PARTIAL_CANCELED,
                List.of(new GatewayCancellation("partial", 10000, Instant.now(), "DONE")));
    }

    @Test
    void 취소_UNKNOWN_조회가_취소전_상태를_보이면_같은_멱등키로_재전송해_완료한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        PaymentCancelRequest request =
                new PaymentCancelRequest(
                        List.of(order.seats().getFirst().reservedSeatId()), "좌석 취소");
        when(gateway.cancel(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TRANSPORT_ERROR"))
                .thenReturn(partialCanceled(payment));
        PaymentOperationResponse pending = pendingCancellation(order, request);
        when(gateway.getPayment(payment.paymentKey())).thenReturn(payment);

        PaymentOperationResponse completed = service.reconcile(8711L, pending.operationId());

        assertThat(completed.operationId()).isEqualTo(pending.operationId());
        assertThat(completed.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(completed.result().status()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
        assertThat(completed.result().balanceAmount()).isEqualTo(10000);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8716",
                                Long.class))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8717",
                                Long.class))
                .isEqualTo(order.reservationId());
        org.mockito.ArgumentCaptor<CancelPaymentCommand> commands =
                org.mockito.ArgumentCaptor.forClass(CancelPaymentCommand.class);
        verify(gateway, times(2)).cancel(commands.capture());
        // 금액·직전 잔액·사유·멱등키가 모두 첫 요청과 같아야 PG의 멱등 처리가 이중 환불을 막는다.
        assertThat(commands.getAllValues().get(1)).isEqualTo(commands.getAllValues().get(0));
        verify(gateway, times(1)).getPayment(payment.paymentKey());
        assertThat(service.reconcile(8711L, pending.operationId())).isEqualTo(completed);
        verify(gateway, times(2)).cancel(any());
    }

    @Test
    void 재전송한_취소가_확정_거절되면_작업을_실패로_끝내고_새_키로_다시_취소할_수_있다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        when(gateway.cancel(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TRANSPORT_ERROR"))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.REJECTED, "REFUND_REJECTED"));
        PaymentOperationResponse pending =
                pendingCancellation(
                        order,
                        new PaymentCancelRequest(
                                List.of(order.seats().getFirst().reservedSeatId()), "좌석 취소"));
        when(gateway.getPayment(payment.paymentKey())).thenReturn(payment);

        PaymentOperationResponse failed = service.reconcile(8711L, pending.operationId());

        assertThat(failed.operationId()).isEqualTo(pending.operationId());
        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("REFUND_REJECTED");
        assertThat(failed.result().status()).isEqualTo(PaymentStatus.DONE);
        assertThat(failed.result().balanceAmount()).isEqualTo(20000);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM screening_seats WHERE"
                                        + " current_reservation_id=?",
                                Integer.class,
                                order.reservationId()))
                .isEqualTo(2);
        assertThat(service.reconcile(8711L, pending.operationId())).isEqualTo(failed);
        verify(gateway, times(2)).cancel(any());

        doReturn(partialCanceled(payment)).when(gateway).cancel(any());
        PaymentOperationResponse retried =
                service.cancel(
                        8711L,
                        order.paymentId(),
                        UUID.randomUUID(),
                        new PaymentCancelRequest(
                                List.of(order.seats().getFirst().reservedSeatId()), "새 키 취소"));

        assertThat(retried.operationId()).isNotEqualTo(pending.operationId());
        assertThat(retried.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(retried.result().balanceAmount()).isEqualTo(10000);
        org.mockito.ArgumentCaptor<CancelPaymentCommand> commands =
                org.mockito.ArgumentCaptor.forClass(CancelPaymentCommand.class);
        verify(gateway, times(3)).cancel(commands.capture());
        assertThat(commands.getAllValues().get(1).idempotencyKey())
                .isEqualTo(commands.getAllValues().get(0).idempotencyKey());
        assertThat(commands.getAllValues().get(2).idempotencyKey())
                .isNotEqualTo(commands.getAllValues().get(0).idempotencyKey());
    }

    @Test
    void 취소_UNKNOWN_조회가_이미_반영된_취소를_보이면_재전송없이_완료한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        when(gateway.cancel(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TRANSPORT_ERROR"));
        PaymentOperationResponse pending =
                pendingCancellation(
                        order,
                        new PaymentCancelRequest(
                                List.of(order.seats().getFirst().reservedSeatId()), "좌석 취소"));
        when(gateway.getPayment(payment.paymentKey())).thenReturn(partialCanceled(payment));

        PaymentOperationResponse completed = service.reconcile(8711L, pending.operationId());

        assertThat(completed.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(completed.result().balanceAmount()).isEqualTo(10000);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8716",
                                Long.class))
                .isNull();
        verify(gateway, times(1)).cancel(any());
    }

    @Test
    void 취소_UNKNOWN_조회가_예상밖_상태면_재전송없이_UNKNOWN을_유지한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        when(gateway.cancel(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TRANSPORT_ERROR"));
        PaymentOperationResponse pending =
                pendingCancellation(
                        order,
                        new PaymentCancelRequest(
                                List.of(order.seats().getFirst().reservedSeatId()), "좌석 취소"));
        GatewayCancellation unrelated =
                new GatewayCancellation("unrelated", 10000, Instant.now(), "DONE");
        List<GatewayPayment> unexpected =
                List.of(
                        // 잔액은 그대로인데 처음 보는 취소 거래가 있다.
                        cancellationResult(
                                payment, 20000, GatewayPaymentStatus.DONE, List.of(unrelated)),
                        cancellationResult(
                                payment,
                                20000,
                                GatewayPaymentStatus.PARTIAL_CANCELED,
                                List.of(unrelated)),
                        // 취소 전 잔액이어도 결제가 취소 가능한 상태가 아니다.
                        cancellationResult(payment, 20000, GatewayPaymentStatus.ABORTED, List.of()),
                        cancellationResult(payment, 20000, GatewayPaymentStatus.CANCELED, List.of()),
                        // 잔액이 다른 값으로 바뀌었다.
                        cancellationResult(payment, 5000, GatewayPaymentStatus.PARTIAL_CANCELED, List.of()),
                        // 다른 결제 정보다.
                        new GatewayPayment(
                                "wrong-key",
                                payment.orderId(),
                                20000,
                                20000,
                                GatewayPaymentStatus.DONE,
                                "카드",
                                payment.approvedAt(),
                                null,
                                List.of()),
                        new GatewayPayment(
                                payment.paymentKey(),
                                payment.orderId(),
                                30000,
                                20000,
                                GatewayPaymentStatus.DONE,
                                "카드",
                                payment.approvedAt(),
                                null,
                                List.of()));
        for (GatewayPayment state : unexpected) {
            when(gateway.getPayment(payment.paymentKey())).thenReturn(state);
            PaymentOperationResponse unknown = service.reconcile(8711L, pending.operationId());
            assertThat(unknown.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
            assertThat(unknown.failureCode()).isEqualTo("INVALID_CANCELLATION_RESULT");
        }

        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId())
                .isEqualTo(pending.operationId());
        assertThat(queries.payment(8711L, order.paymentId()).balanceAmount()).isEqualTo(20000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM screening_seats WHERE"
                                        + " current_reservation_id=?",
                                Integer.class,
                                order.reservationId()))
                .isEqualTo(2);
        verify(gateway, times(1)).cancel(any());
        verify(gateway, times(unexpected.size())).getPayment(payment.paymentKey());
    }

    // ---- PG 키 설정 확인 ----

    @Test
    void PG_설정이_없으면_승인은_DB를_바꾸기_전에_거절하고_설정_후_같은_요청이_진행된다() {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        payment.paymentKey(), order.orderId(), order.totalAmount());
        UUID key = UUID.randomUUID();
        doThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "NOT_CONFIGURED"))
                .when(gateway)
                .verifyReady();

        assertThatThrownBy(() -> service.confirm(8711L, key, request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_operation", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM payment WHERE id=?",
                                String.class,
                                order.paymentId()))
                .isEqualTo("READY");
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        verifyNoGatewayRequests();

        doNothing().when(gateway).verifyReady();
        when(gateway.confirm(any())).thenReturn(payment);
        assertThat(service.confirm(8711L, key, request).status())
                .isEqualTo(PaymentOperationStatus.SUCCEEDED);
    }

    @Test
    void PG_설정이_없으면_취소와_예매_취소는_작업을_만들지_않고_거절한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        doThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TEST_KEYS_REQUIRED"))
                .when(gateway)
                .verifyReady();

        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(null, "전체")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        assertThatThrownBy(
                        () ->
                                service.cancelReservation(
                                        8711L, order.reservationId(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_operation", Integer.class))
                .isEqualTo(1);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(queries.payment(8711L, order.paymentId()).balanceAmount()).isEqualTo(20000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RESERVED");
        verify(gateway, never()).cancel(any());
    }

    @Test
    void PG_설정이_없으면_복구도_PG_조회_없이_거절하고_작업은_UNKNOWN으로_남는다() {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        when(gateway.confirm(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        PaymentOperationResponse pending =
                service.confirm(
                        8711L,
                        UUID.randomUUID(),
                        new PaymentConfirmRequest(
                                payment.paymentKey(), order.orderId(), order.totalAmount()));
        assertThat(pending.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        doThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "NOT_CONFIGURED"))
                .when(gateway)
                .verifyReady();

        assertThatThrownBy(() -> service.reconcile(8711L, pending.operationId()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(queries.operation(8711L, pending.operationId()).status())
                .isEqualTo(PaymentOperationStatus.UNKNOWN);
        verify(gateway, never()).getPayment(any());
    }

    // ---- 승인 UNKNOWN + 조회 NOT_FOUND ----

    @Test
    void 승인_UNKNOWN_조회가_NOT_FOUND여도_유예전에는_유지하고_유예후에는_선점을_풀어준다() {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        when(gateway.confirm(any()))
                .thenThrow(new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT"));
        UUID key = UUID.randomUUID();
        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        payment.paymentKey(), order.orderId(), order.totalAmount());
        PaymentOperationResponse pending = service.confirm(8711L, key, request);
        assertThat(pending.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        when(gateway.getPayment(payment.paymentKey()))
                .thenThrow(
                        new PaymentGatewayException(
                                GatewayFailureType.NOT_FOUND, "NOT_FOUND_PAYMENT"));

        // 선점 기한은 지났지만 유예(30분) 안이면 아직 단정하지 않는다.
        setOrderExpiry(order.paymentId(), Instant.now().minus(java.time.Duration.ofMinutes(10)));
        PaymentOperationResponse within = service.reconcile(8711L, pending.operationId());
        assertThat(within.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(within.failureCode()).isEqualTo("NOT_FOUND_PAYMENT");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("PAYMENT_PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8716",
                                Long.class))
                .isEqualTo(order.reservationId());
        assertThatThrownBy(
                        () ->
                                holds.create(
                                        8711L,
                                        UUID.randomUUID(),
                                        new SeatHoldCreateRequest(
                                                8715L, List.of(new ReservedSeatRequest("A", 2)))))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.HOLD_LIMIT_REACHED);

        setOrderExpiry(order.paymentId(), Instant.now().minus(java.time.Duration.ofMinutes(31)));
        PaymentOperationResponse failed = service.reconcile(8711L, pending.operationId());

        assertThat(failed.operationId()).isEqualTo(pending.operationId());
        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("GATEWAY_NOT_FOUND");
        assertThat(failed.result().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RELEASED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM screening_seats WHERE"
                                        + " current_reservation_id=?",
                                Integer.class,
                                order.reservationId()))
                .isZero();
        assertThat(service.reconcile(8711L, pending.operationId())).isEqualTo(failed);
        assertThat(service.confirm(8711L, key, request)).isEqualTo(failed);
        assertThat(
                        holds.create(
                                        8711L,
                                        UUID.randomUUID(),
                                        new SeatHoldCreateRequest(
                                                8715L, List.of(new ReservedSeatRequest("A", 1))))
                                .created())
                .isTrue();
        verify(gateway, times(1)).confirm(any());
        verify(gateway, times(2)).getPayment(payment.paymentKey());
    }

    // ---- 확정 단계의 좌석 NOWAIT 충돌 재시도 ----

    private void stubInTransaction(Runnable stubbing) {
        // 스파이 스텁은 호출자 트랜잭션 안에서 설정하는 기존 방식을 따른다.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> stubbing.run());
    }

    @Test
    void 승인_확정이_좌석_NOWAIT_충돌을_한번_만나도_재시도로_완료한다() {
        PaymentOrderResponse order = order(1);
        stubInTransaction(
                () ->
                        doThrow(new BusinessException(ErrorCode.SEAT_BUSY))
                                .doCallRealMethod()
                                .when(reservationPayments)
                                .confirmPayment(order.reservationId(), 8711L));

        PaymentOperationResponse result = confirm(order);

        assertThat(result.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(result.result().status()).isEqualTo(PaymentStatus.DONE);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RESERVED");
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        verify(reservationPayments, times(2)).confirmPayment(order.reservationId(), 8711L);
        verify(gateway, times(1)).confirm(any());
        verify(gateway, never()).getPayment(any());
    }

    @Test
    void 승인_확정이_계속_충돌하면_세번만_시도하고_UNKNOWN으로_남아_조회로_완료한다() {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        stubInTransaction(
                () ->
                        doThrow(new BusinessException(ErrorCode.SEAT_BUSY))
                                .when(reservationPayments)
                                .confirmPayment(order.reservationId(), 8711L));

        PaymentOperationResponse unknown = confirm(order);

        assertThat(unknown.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(unknown.failureCode()).isEqualTo("FINALIZATION_PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("PAYMENT_PENDING");
        verify(reservationPayments, times(3)).confirmPayment(order.reservationId(), 8711L);

        stubInTransaction(
                () ->
                        doCallRealMethod()
                                .when(reservationPayments)
                                .confirmPayment(order.reservationId(), 8711L));
        when(gateway.getPayment(payment.paymentKey())).thenReturn(payment);
        PaymentOperationResponse completed = service.reconcile(8711L, unknown.operationId());

        assertThat(completed.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RESERVED");
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 좌석_NOWAIT_충돌이_아닌_확정_실패는_재시도하지_않고_바로_보상취소로_넘어간다() {
        PaymentOrderResponse order = order(1);
        GatewayPayment payment = done(order);
        stubInTransaction(
                () ->
                        doThrow(new BusinessException(ErrorCode.SEAT_OWNER_MISMATCH))
                                .when(reservationPayments)
                                .confirmPayment(order.reservationId(), 8711L));
        when(gateway.cancel(any()))
                .thenReturn(
                        cancellationResult(
                                payment,
                                0,
                                GatewayPaymentStatus.CANCELED,
                                List.of(
                                        new GatewayCancellation(
                                                "compensation",
                                                payment.totalAmount(),
                                                Instant.now(),
                                                "DONE"))));

        PaymentOperationResponse failed = confirm(order);

        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("APPROVAL_COMPENSATED");
        verify(reservationPayments, times(1)).confirmPayment(order.reservationId(), 8711L);
        verify(gateway, times(1)).cancel(any());
    }

    @Test
    void 취소_확정이_좌석_NOWAIT_충돌을_한번_만나도_재시도로_완료한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        List<Long> ids = List.of(order.seats().getFirst().reservedSeatId());
        stubInTransaction(
                () ->
                        doThrow(new BusinessException(ErrorCode.SEAT_BUSY))
                                .doCallRealMethod()
                                .when(reservationPayments)
                                .cancelPaidSeats(
                                        eq(order.reservationId()),
                                        eq(8711L),
                                        eq(ids),
                                        any(Instant.class)));
        when(gateway.cancel(any())).thenReturn(partialCanceled(payment));

        PaymentOperationResponse result =
                service.cancel(
                        8711L,
                        order.paymentId(),
                        UUID.randomUUID(),
                        new PaymentCancelRequest(ids, "좌석 취소"));

        assertThat(result.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(result.result().balanceAmount()).isEqualTo(10000);
        assertThat(queries.payment(8711L, order.paymentId()).activeOperationId()).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_reservation_id FROM screening_seats WHERE"
                                        + " screening_seat_id=8716",
                                Long.class))
                .isNull();
        verify(reservationPayments, times(2))
                .cancelPaidSeats(
                        eq(order.reservationId()), eq(8711L), eq(ids), any(Instant.class));
        verify(gateway, times(1)).cancel(any());
        verify(gateway, never()).getPayment(any());
    }

    @Test
    void 취소_확정이_계속_충돌하면_세번만_시도하고_UNKNOWN으로_남아_조회로_완료한다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        List<Long> ids = List.of(order.seats().getFirst().reservedSeatId());
        stubInTransaction(
                () ->
                        doThrow(new BusinessException(ErrorCode.SEAT_BUSY))
                                .when(reservationPayments)
                                .cancelPaidSeats(
                                        eq(order.reservationId()),
                                        eq(8711L),
                                        eq(ids),
                                        any(Instant.class)));
        GatewayPayment partial = partialCanceled(payment);
        when(gateway.cancel(any())).thenReturn(partial);

        PaymentOperationResponse unknown =
                service.cancel(
                        8711L,
                        order.paymentId(),
                        UUID.randomUUID(),
                        new PaymentCancelRequest(ids, "좌석 취소"));

        assertThat(unknown.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(unknown.failureCode()).isEqualTo("FINALIZATION_PENDING");
        assertThat(queries.payment(8711L, order.paymentId()).balanceAmount()).isEqualTo(20000);
        verify(reservationPayments, times(3))
                .cancelPaidSeats(
                        eq(order.reservationId()), eq(8711L), eq(ids), any(Instant.class));

        stubInTransaction(
                () ->
                        doCallRealMethod()
                                .when(reservationPayments)
                                .cancelPaidSeats(
                                        eq(order.reservationId()),
                                        eq(8711L),
                                        eq(ids),
                                        any(Instant.class)));
        when(gateway.getPayment(payment.paymentKey())).thenReturn(partial);
        PaymentOperationResponse completed = service.reconcile(8711L, unknown.operationId());

        assertThat(completed.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(completed.result().balanceAmount()).isEqualTo(10000);
        verify(gateway, times(1)).cancel(any());
    }

    @Test
    void 재시도_대기_중_인터럽트되면_더_시도하지_않고_인터럽트_상태를_보존한다() {
        PaymentOrderResponse order = order(1);
        stubInTransaction(
                () ->
                        doAnswer(
                                        call -> {
                                            Thread.currentThread().interrupt();
                                            throw new BusinessException(ErrorCode.SEAT_BUSY);
                                        })
                                .when(reservationPayments)
                                .confirmPayment(order.reservationId(), 8711L));
        PaymentOperationResponse unknown;
        try {
            unknown = confirm(order);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(unknown.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(unknown.failureCode()).isEqualTo("FINALIZATION_PENDING");
        verify(reservationPayments, times(1)).confirmPayment(order.reservationId(), 8711L);
    }

    // ---- 전체 취소 후 재취소와 취소 시각 ----

    @Test
    void 이미_전체_취소된_결제의_새키_전체취소는_409로_거절하고_PG를_호출하지_않는다() throws Exception {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        when(gateway.cancel(any()))
                .thenReturn(
                        cancellationResult(
                                payment,
                                0,
                                GatewayPaymentStatus.CANCELED,
                                List.of(
                                        new GatewayCancellation(
                                                "full", 20000, Instant.now(), "DONE"))));
        UUID fullKey = UUID.randomUUID();
        PaymentCancelRequest full = new PaymentCancelRequest(null, "전체 취소");
        PaymentOperationResponse canceled = service.cancel(8711L, order.paymentId(), fullKey, full);
        assertThat(canceled.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        clearInvocations(gateway);

        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(null, "다시 전체 취소")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_ALREADY_CANCELED);
        assertThatThrownBy(
                        () ->
                                service.cancelReservation(
                                        8711L, order.reservationId(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_ALREADY_CANCELED);
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                        "/api/v1/reservations/" + order.reservationId())
                                .header("Authorization", token(8711L))
                                .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict());
        // 이미 취소된 특정 좌석을 지정한 부분취소는 기존대로 좌석 오류다.
        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(
                                                List.of(order.seats().getFirst().reservedSeatId()),
                                                "취소된 좌석")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SEAT);
        // 같은 키의 완료 재전송은 저장된 응답을 그대로 돌려준다.
        assertThat(service.cancel(8711L, order.paymentId(), fullKey, full)).isEqualTo(canceled);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_operation", Integer.class))
                .isEqualTo(2);
        verify(gateway, never()).cancel(any());
        verify(gateway, never()).getPayment(any());
    }

    @Test
    void 좌석_취소_시각은_예매_좌석과_주문_좌석에_같은_값으로_저장된다() {
        PaymentOrderResponse order = order(2);
        confirm(order);
        GatewayPayment payment = done(order);
        when(gateway.cancel(any()))
                .thenReturn(
                        cancellationResult(
                                payment,
                                0,
                                GatewayPaymentStatus.CANCELED,
                                List.of(
                                        new GatewayCancellation(
                                                "full", 20000, Instant.now(), "DONE"))));

        assertThat(
                        service.cancel(
                                        8711L,
                                        order.paymentId(),
                                        UUID.randomUUID(),
                                        new PaymentCancelRequest(null, "전체 취소"))
                                .status())
                .isEqualTo(PaymentOperationStatus.SUCCEEDED);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM reserved_seats rs JOIN payment_order_seat pos"
                                        + " ON pos.reserved_seat_id = rs.reserved_seat_id WHERE"
                                        + " rs.reservation_id=? AND rs.canceled_at IS NOT NULL AND"
                                        + " pos.canceled_at IS NOT NULL AND rs.canceled_at ="
                                        + " pos.canceled_at",
                                Integer.class,
                                order.reservationId()))
                .isEqualTo(2);
    }
}
