package com.ceos.cgv.domain.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ceos.cgv.domain.payment.dto.request.*;
import com.ceos.cgv.domain.payment.dto.response.*;
import com.ceos.cgv.domain.payment.enums.*;
import com.ceos.cgv.domain.payment.gateway.*;
import com.ceos.cgv.domain.reservation.dto.*;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldService;
import com.ceos.cgv.domain.reservation.service.payment.ReservationPaymentService;
import com.ceos.cgv.global.exception.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentWorkflowIntegrationTest {
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
        verifyNoInteractions(gateway);
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

    @Test
    void 부분취소_후_전체취소와_이전결과_재전송은_누적잔액을_보존한다() {
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
                                            ? GatewayPaymentStatus.CANCELED
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
        assertThat(partial.result().balanceAmount()).isEqualTo(10000);
        PaymentOperationResponse full =
                service.cancel(
                        8711L, order.paymentId(), fullKey, new PaymentCancelRequest(null, "전체 취소"));
        assertThat(full.result().balanceAmount()).isZero();
        assertThat(
                        service.cancel(
                                8711L,
                                order.paymentId(),
                                fullKey,
                                new PaymentCancelRequest(null, "전체 취소")))
                .isEqualTo(full);
        assertThat(service.cancel(8711L, order.paymentId(), partKey, part)).isEqualTo(partial);
        assertThat(queries.operation(8711L, approved.operationId()).result().status())
                .isEqualTo(PaymentStatus.DONE);
        verify(gateway, times(2)).cancel(any());
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
        jdbc.update(
                "UPDATE reservations SET expires_at=? WHERE reservation_id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
                order.reservationId());
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
        verifyNoInteractions(gateway);
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

    @Test
    void PG_성공후_영구좌석확정실패는_고정키_보상취소_후에만_해제한다() {
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
                        GatewayPaymentStatus.CANCELED,
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
        verifyNoInteractions(gateway);
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
        verifyNoInteractions(gateway);
        jdbc.update("UPDATE movies SET visibility='PUBLIC' WHERE movie_id=8714");
        when(gateway.confirm(any())).thenReturn(payment);
        PaymentOperationResponse approved = service.confirm(8711L, key, request);
        jdbc.update("UPDATE movies SET visibility='HIDDEN' WHERE movie_id=8714");
        jdbc.update(
                "UPDATE reservations SET expires_at=? WHERE reservation_id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
                order.reservationId());
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
        verify(gateway, times(1)).cancel(any());
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

    @Test
    void 조회에서_승인_전체취소가_확인되면_다시_취소하지_않고_실패로_종결한다() {
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
                                0,
                                GatewayPaymentStatus.CANCELED,
                                "카드",
                                payment.approvedAt(),
                                null,
                                List.of(
                                        new GatewayCancellation(
                                                "external",
                                                payment.totalAmount(),
                                                Instant.now(),
                                                "DONE"))));
        PaymentOperationResponse failed = service.reconcile(8711L, pending.operationId());
        assertThat(failed.status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("APPROVAL_EXTERNALLY_CANCELED");
        assertThat(failed.result().balanceAmount()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM reservations WHERE reservation_id=?",
                                String.class,
                                order.reservationId()))
                .isEqualTo("RELEASED");
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
                                        .cancelPaidSeats(order.reservationId(), 8711L, ids));
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
}
