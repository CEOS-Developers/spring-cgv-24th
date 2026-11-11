package com.spring_cgv_24th.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.payment.dto.PaymentCancelReqDTO;
import com.spring_cgv_24th.domain.payment.dto.PaymentPartialCancelReqDTO;
import com.spring_cgv_24th.domain.payment.dto.PaymentReqDTO;
import com.spring_cgv_24th.domain.payment.dto.PaymentResDTO;
import com.spring_cgv_24th.domain.payment.enums.PaymentStatus;
import com.spring_cgv_24th.domain.payment.enums.PaymentTargetType;
import com.spring_cgv_24th.domain.payment.gateway.PaymentGateway;
import com.spring_cgv_24th.domain.payment.service.PaymentService;
import com.spring_cgv_24th.domain.reservation.dto.ReservationReqDTO;
import com.spring_cgv_24th.domain.reservation.service.ReservationService;
import com.spring_cgv_24th.domain.screening.repository.ScreeningSeatRepository;
import com.spring_cgv_24th.domain.store.dto.StoreOrderReqDTO;
import com.spring_cgv_24th.domain.store.dto.StoreOrderResDTO;
import com.spring_cgv_24th.domain.store.service.StoreService;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = "payment.recovery-batch-size=2")
class ConcurrencyIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int FIRST_SEAT_PRICE = 14_000;
    private static final int PRODUCT_PRICE = 5_000;
    private static final PaymentCancelReqDTO CANCEL_REQUEST = new PaymentCancelReqDTO("단순 변심");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReservationService reservationService;
    @Autowired private StoreService storeService;
    @Autowired private PaymentService paymentService;
    @MockitoBean private Clock clock;
    @MockitoBean private PaymentGateway gateway;
    @MockitoSpyBean private ScreeningSeatRepository screeningSeatRepository;

    private long memberId, theaterId, auditoriumId, screeningId, seatId, productId;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZONE);
        setTime(NOW);
        memberId = insert("insert into member (email, name, password_hash, role) values (?, '동시성 회원', 'unused', 'USER')",
                UUID.randomUUID() + "@test.local");
        theaterId = insert("insert into theater (name, address) values ('동시성 영화관', '테스트')");
        long typeId = jdbc.queryForObject("select auditorium_type_id from auditorium_type where kind = 'GENERAL'", Long.class);
        auditoriumId = insert("insert into auditorium (theater_id, type_id, name) values (?, ?, '1관')", theaterId, typeId);
        long movieId = insert("insert into movie (title, duration_minutes, age_rating) values ('동시성 영화', 90, 'ALL')");
        LocalDateTime start = NOW.plusDays(1);
        screeningId = insert("insert into screening (movie_id, auditorium_id, starts_at, ends_at) values (?, ?, ?, ?)",
                movieId, auditoriumId, start, start.plusMinutes(90));
        seatId = insertSeat(1, FIRST_SEAT_PRICE).id();
        productId = insert("insert into product (name, price) values ('팝콘', ?)", PRODUCT_PRICE);
        insert("insert into theater_stock (theater_id, product_id, quantity, updated_at) values (?, ?, 6, CURRENT_TIMESTAMP)",
                theaterId, productId);
    }

    // 좌석과 재고 동시성
    @Test
    void sameSeatCanOnlyBeReservedOnce() throws Exception {
        var request = new ReservationReqDTO.CreateReservationDTO(screeningId, List.of(seatId));

        var results = runTogether(10, () -> reservationService.createReservation(memberId, request));

        assertResults(results, 1, ErrorCode.SEAT_ALREADY_RESERVED);
        assertThat(count("select count(*) from reservation where screening_id = ?", screeningId)).isEqualTo(1);
        assertThat(count("select count(*) from screening_seat where screening_seat_id = ? and reservation_id is not null", seatId))
                .isEqualTo(1);
        assertThat(count("select count(*) from reservation_seat where screening_seat_id = ?", seatId)).isEqualTo(1);
    }

    @Test
    void concurrentOrdersKeepStockAndPurchasedQuantityConsistent() throws Exception {

        var results = runTogether(10, () -> createOrder(1));

        assertResults(results, 5, ErrorCode.STORE_STOCK_INSUFFICIENT);
        assertThat(count("select quantity from theater_stock where theater_id = ?", theaterId)).isEqualTo(1);
        assertThat(count("select count(*) from store_order where theater_id = ?", theaterId)).isEqualTo(5);
        assertThat(count("select sum(quantity) from store_order_item where product_id = ?", productId)).isEqualTo(5);
    }

    @ParameterizedTest
    @CsvSource({"9, 4, PENDING_PAYMENT", "10, 6, EXPIRED"})
    void concurrentExpirationRestoresStockOnlyOnceAfterTenMinutes(
            int elapsedMinutes, int expectedStock, String expectedStatus) throws Exception {
        long orderId = createOrder(2).orderId();
        setTime(NOW.plusMinutes(elapsedMinutes));

        var results = runTogether(10, () -> {
            storeService.releaseUnpaidOrderStock();
            return null;
        });
        storeService.releaseUnpaidOrderStock();

        assertThat(results).containsOnlyNulls();
        assertThat(count("select quantity from theater_stock where theater_id = ?", theaterId)).isEqualTo(expectedStock);
        assertThat(jdbc.queryForObject("select status from store_order where store_order_id = ?", String.class, orderId))
                .isEqualTo(expectedStatus);
    }

    @Test
    void oneFailedStockRestoreDoesNotRollBackAnotherOrder() {
        long first = createOrder(2).orderId();
        long second = createOrder(2).orderId();
        long missingStockProduct = insert("insert into product (name, price) values ('재고 없음', 5000)");
        try {
            insert("insert into store_order_item (order_id, product_id, quantity, unit_price) values (?, ?, 1, 5000)",
                    first, missingStockProduct);
            setTime(NOW.plusMinutes(10));

            storeService.releaseUnpaidOrderStock();

            String orderStatus = "select status from store_order where store_order_id = ?";
            assertThat(jdbc.queryForObject(orderStatus, String.class, first)).isEqualTo("PENDING_PAYMENT");
            assertThat(jdbc.queryForObject(orderStatus, String.class, second)).isEqualTo("EXPIRED");
            assertThat(count("select quantity from theater_stock where theater_id = ?", theaterId)).isEqualTo(4);
        } finally {
            jdbc.update("delete from store_order_item where product_id = ?", missingStockProduct);
            jdbc.update("delete from product where product_id = ?", missingStockProduct);
        }
    }

    @Test
    void completedOrdersDoNotExpireOrRestoreStock() {
        long orderId = createOrder(2).orderId();
        jdbc.update("update store_order set status = 'COMPLETED', expires_at = null where store_order_id = ?", orderId);
        setTime(NOW.plusMinutes(10));

        storeService.releaseUnpaidOrderStock();

        assertThat(count("select quantity from theater_stock where theater_id = ?", theaterId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select status from store_order where store_order_id = ?", String.class, orderId))
                .isEqualTo("COMPLETED");
    }

    @ParameterizedTest
    @CsvSource({"9, 1, PENDING_PAYMENT", "10, 0, EXPIRED"})
    void unpaidReservationReleasesSeatsOnlyAfterTenMinutes(int minutes, int occupied, String status) throws Exception {
        Target target = paymentTarget(PaymentTargetType.RESERVATION);
        setTime(NOW.plusMinutes(minutes));

        assertThat(runTogether(10, () -> { reservationService.releaseUnpaidSeats(); return null; })).containsOnlyNulls();
        reservationService.releaseUnpaidSeats();

        assertThat(count("select count(*) from screening_seat where screening_seat_id = ? and reservation_id is not null", seatId))
                .isEqualTo(occupied);
        assertThat(targetStatus(target)).isEqualTo(status);
    }

    @Test
    void failedSeatReleaseDoesNotRollBackOrBlockOtherReservations() {
        Target first = reservationTarget(List.of(seatId));
        Target failed = reservationTarget(List.of(insertSeat(2, FIRST_SEAT_PRICE).id()));
        Target last = reservationTarget(List.of(insertSeat(3, FIRST_SEAT_PRICE).id()));
        doThrow(new IllegalStateException("좌석 잠금 실패"))
                .when(screeningSeatRepository).findAllByReservationIdForUpdate(failed.id());
        setTime(NOW.plusMinutes(10));

        reservationService.releaseUnpaidSeats();

        assertThat(List.of(targetStatus(first), targetStatus(failed), targetStatus(last)))
                .containsExactly("EXPIRED", "PENDING_PAYMENT", "EXPIRED");
        assertThat(jdbc.queryForList("select reservation_id from screening_seat where screening_id = ? order by column_no",
                Long.class, screeningId)).containsExactly(null, failed.id(), null);
    }

    // 결제 승인과 복구
    @ParameterizedTest
    @EnumSource(PaymentTargetType.class)
    void invalidPaymentRequestsAreRejectedBeforeCallingToss(PaymentTargetType type) {
        Target target = paymentTarget(type);
        assertError(ErrorCode.PAYMENT_FORBIDDEN,
                () -> paymentService.confirm(memberId + 1, type, target.id(), paymentRequest(target)));
        assertError(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                () -> paymentService.confirm(memberId, type, target.id(),
                new PaymentReqDTO("payment-key", target.orderId(), 1L)));
        assertError(ErrorCode.PAYMENT_REQUEST_CONFLICT,
                () -> paymentService.confirm(memberId, type, target.id(),
                new PaymentReqDTO("payment-key", "another-order", target.amount())));
        setTime(NOW.plusMinutes(10));
        assertError(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE,
                () -> paymentService.confirm(memberId, type, target.id(), paymentRequest(target)));
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @EnumSource(PaymentTargetType.class)
    void concurrentConfirmationsCallTossOnceAndKeepReservedResources(PaymentTargetType type) throws Exception {
        Target target = paymentTarget(type);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            setTime(NOW.plusMinutes(10));
            releaseUnpaidResources();
            assertResources(target, true);
            if (type == PaymentTargetType.RESERVATION) {
                assertError(ErrorCode.RESERVATION_PAYMENT_CANCELLATION_REQUIRED,
                        () -> reservationService.cancelReservation(target.id(), memberId));
            }
            return approved(target, target.amount());
        });

        var results = runTogether(10, () -> paymentService.confirm(memberId, type, target.id(), paymentRequest(target)));

        assertThat(results.stream().filter(Objects::isNull).count()).isGreaterThanOrEqualTo(1);
        assertThat(results.stream().filter(Objects::nonNull)).containsOnly(ErrorCode.PAYMENT_PENDING);
        assertThat(paymentService.getPayment(memberId, type, target.id()).status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentService.confirm(memberId, type, target.id(), paymentRequest(target)).amount()).isEqualTo(target.amount());
        verify(gateway, times(1)).confirm("payment-key", target.orderId(), target.amount(), "confirm-" + target.orderId());
        if (type == PaymentTargetType.RESERVATION) {
            assertError(ErrorCode.RESERVATION_PAYMENT_CANCELLATION_REQUIRED,
                    () -> reservationService.cancelReservation(target.id(), memberId));
            assertResources(target, true);
        }
        Target other = paymentTarget(type == PaymentTargetType.RESERVATION
                ? PaymentTargetType.STORE_ORDER : PaymentTargetType.RESERVATION);
        assertError(ErrorCode.PAYMENT_REQUEST_CONFLICT,
                () -> paymentService.confirm(memberId, other.type(), other.id(), paymentRequest(other)));
    }

    @ParameterizedTest
    @EnumSource(PaymentTargetType.class)
    void rejectedPaymentEndsTargetAndReleasesResourcesOnTheNextSweep(PaymentTargetType type) {
        Target target = paymentTarget(type);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenThrow(new PaymentGateway.Failure(true));

        assertError(ErrorCode.PAYMENT_CONFIRMATION_FAILED,
                () -> paymentService.confirm(memberId, type, target.id(), paymentRequest(target)));
        assertThat(targetStatus(target)).isEqualTo("PAYMENT_FAILED");
        assertResources(target, true);

        releaseUnpaidResources();
        releaseUnpaidResources();

        assertThat(targetStatus(target)).isEqualTo("CANCELLED");
        assertResources(target, false);
        assertThat(paymentService.getPayment(memberId, type, target.id()).status()).isEqualTo(PaymentStatus.FAILED);
    }

    @ParameterizedTest
    @CsvSource({"STORE_ORDER, false", "RESERVATION, true"})
    void uncertainPaymentKeepsResourcesAndRecoversThroughLookup(PaymentTargetType type, boolean invalidResponse) {
        Target target = paymentTarget(type);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenAnswer(invocation -> {
            if (invalidResponse) return approved(target, 1);
            throw new PaymentGateway.Failure(false);
        });
        assertError(ErrorCode.PAYMENT_GATEWAY_ERROR,
                () -> paymentService.confirm(memberId, type, target.id(), paymentRequest(target)));
        setTime(NOW.plusMinutes(11));
        releaseUnpaidResources();
        assertThat(targetStatus(target)).isEqualTo("PAYMENT_PROCESSING");
        assertResources(target, true);

        when(gateway.find("payment-key")).thenReturn(approved(target, target.amount()));
        assertThat(paymentService.getPayment(memberId, type, target.id()).status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @ParameterizedTest
    @CsvSource({
            "STORE_ORDER, PROCESSING, DONE, COMPLETED, true",
            "RESERVATION, UNKNOWN, DONE, CONFIRMED, true",
            "STORE_ORDER, UNKNOWN, EXPIRED, CANCELLED, false",
            "RESERVATION, PROCESSING, ABORTED, CANCELLED, false",
            "STORE_ORDER, UNKNOWN, ERROR, PAYMENT_PROCESSING, true",
            "RESERVATION, PROCESSING, ERROR, PAYMENT_PROCESSING, true"
    })
    void overduePaymentsAreAutomaticallyRechecked(PaymentTargetType type, String initialStatus,
                                                  String responseStatus, String expectedStatus, boolean resourcesHeld) {
        Target target = paymentTarget(type);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenThrow(new PaymentGateway.Failure(false));
        assertThrows(CustomException.class, () -> paymentService.confirm(memberId, type, target.id(), paymentRequest(target)));
        jdbc.update("update payment set status = ? where target_type = ? and target_id = ?", initialStatus, type.name(), target.id());
        setTime(NOW.plusMinutes(9));
        paymentService.recoverUnresolvedPayments();
        verify(gateway, never()).find(anyString());

        when(gateway.find("payment-key")).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (responseStatus.equals("ERROR")) throw new PaymentGateway.Failure(false);
            if (responseStatus.equals("DONE")) return approved(target, target.amount());
            return new PaymentGateway.Result("payment-key", target.orderId(), responseStatus, "KRW", target.amount(), 0L, null, null);
        });
        setTime(NOW.plusMinutes(10));
        paymentService.recoverUnresolvedPayments();
        releaseUnpaidResources();
        paymentService.recoverUnresolvedPayments();
        releaseUnpaidResources();

        assertThat(targetStatus(target)).isEqualTo(expectedStatus);
        assertResources(target, resourcesHeld);
        verify(gateway, times(responseStatus.equals("ERROR") ? 2 : 1)).find("payment-key");
    }

    @ParameterizedTest
    @EnumSource(PaymentTargetType.class)
    void missingApprovalEventuallyReleasesResources(PaymentTargetType type) {
        Target target = paymentTarget(type);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new PaymentGateway.Failure(false));
        assertThrows(CustomException.class,
                () -> paymentService.confirm(memberId, type, target.id(), paymentRequest(target)));
        when(gateway.find("payment-key"))
                .thenThrow(new PaymentGateway.Failure(PaymentGateway.Failure.Kind.NOT_FOUND));

        setTime(NOW.plusMinutes(39));
        paymentService.recoverUnresolvedPayments();
        releaseUnpaidResources();
        assertResources(target, true);

        setTime(NOW.plusMinutes(40));
        paymentService.recoverUnresolvedPayments();
        releaseUnpaidResources();
        assertThat(paymentService.getPayment(memberId, type, target.id()).status()).isEqualTo(PaymentStatus.FAILED);
        assertResources(target, false);
    }

    @Test
    void failedLookupDoesNotBlockLaterBatchesAndIsRetriedNextRound() {
        Target order = paymentTarget(PaymentTargetType.STORE_ORDER);
        Target reservation = paymentTarget(PaymentTargetType.RESERVATION);
        Target laterReservation = reservationTarget(List.of(insertSeat(2, FIRST_SEAT_PRICE).id()));
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenThrow(new PaymentGateway.Failure(false));
        assertThrows(CustomException.class,
                () -> paymentService.confirm(memberId, order.type(), order.id(), paymentRequest(order)));
        assertThrows(CustomException.class, () -> paymentService.confirm(memberId, reservation.type(), reservation.id(),
                new PaymentReqDTO("reservation-payment-key", reservation.orderId(), reservation.amount())));
        assertThrows(CustomException.class, () -> paymentService.confirm(memberId, laterReservation.type(), laterReservation.id(),
                new PaymentReqDTO("later-payment-key", laterReservation.orderId(), laterReservation.amount())));
        when(gateway.find("payment-key")).thenThrow(new PaymentGateway.Failure(false));
        when(gateway.find("reservation-payment-key")).thenReturn(new PaymentGateway.Result(
                "reservation-payment-key", reservation.orderId(), "DONE", "KRW", reservation.amount(), reservation.amount(),
                "카드", NOW.atZone(ZONE).toOffsetDateTime()));
        when(gateway.find("later-payment-key")).thenReturn(new PaymentGateway.Result(
                "later-payment-key", laterReservation.orderId(), "DONE", "KRW",
                laterReservation.amount(), laterReservation.amount(), "카드", NOW.atZone(ZONE).toOffsetDateTime()));
        setTime(NOW.plusMinutes(10));

        paymentService.recoverUnresolvedPayments();

        assertThat(targetStatus(order)).isEqualTo("PAYMENT_PROCESSING");
        assertThat(targetStatus(reservation)).isEqualTo("CONFIRMED");
        assertThat(targetStatus(laterReservation)).isEqualTo("PAYMENT_PROCESSING");
        verify(gateway, never()).find("later-payment-key");

        paymentService.recoverUnresolvedPayments();

        assertThat(targetStatus(laterReservation)).isEqualTo("CONFIRMED");
        verify(gateway, times(1)).find("payment-key");

        paymentService.recoverUnresolvedPayments();

        verify(gateway, times(2)).find("payment-key");
        assertResources(order, true);
        assertResources(reservation, true);
    }

    // 전체 취소
    @ParameterizedTest
    @EnumSource(PaymentTargetType.class)
    void fullCancellationRestoresResourcesOnlyOnce(PaymentTargetType type) throws Exception {
        Target target = paymentTarget(type);
        assertError(ErrorCode.PAYMENT_FORBIDDEN,
                () -> paymentService.cancel(memberId + 1, type, target.id(), CANCEL_REQUEST));
        assertError(ErrorCode.PAYMENT_NOT_FOUND,
                () -> paymentService.cancel(memberId, type, target.id(), CANCEL_REQUEST));
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(approved(target, target.amount()));
        paymentService.confirm(memberId, type, target.id(), paymentRequest(target));
        when(gateway.cancel(anyString(), anyString(), isNull(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertResources(target, true);
            setTime(NOW.plusDays(1));
            return cancelled(target);
        });

        var results = runTogether(10, () -> paymentService.cancel(memberId, type, target.id(), CANCEL_REQUEST));

        assertThat(results.stream().filter(Objects::isNull).count()).isGreaterThanOrEqualTo(1);
        assertThat(results.stream().filter(Objects::nonNull)).containsOnly(ErrorCode.PAYMENT_PENDING);
        assertThat(paymentService.cancel(memberId, type, target.id(), CANCEL_REQUEST).status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(paymentService.getPayment(memberId, type, target.id()).balanceAmount()).isZero();
        assertThat(targetStatus(target)).isEqualTo("CANCELLED");
        assertResources(target, false);
        verify(gateway, times(1)).cancel("payment-key", "단순 변심", null, "cancel-" + target.orderId());
        assertError(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE,
                () -> paymentService.confirm(memberId, type, target.id(), paymentRequest(target)));
    }

    @ParameterizedTest
    @CsvSource({"STORE_ORDER, TIMEOUT", "RESERVATION, WRONG_BALANCE", "STORE_ORDER, MISSING_BALANCE"})
    void uncertainCancellationKeepsResourcesUntilAutomaticLookupConfirmsIt(PaymentTargetType type, String failure) {
        Target target = paidTarget(type);
        when(gateway.cancel(anyString(), anyString(), isNull(), anyString())).thenAnswer(invocation -> {
            if ("TIMEOUT".equals(failure)) throw new PaymentGateway.Failure(false);
            Long balance = "MISSING_BALANCE".equals(failure) ? null : 1L;
            return new PaymentGateway.Result("payment-key", target.orderId(), "CANCELED", "KRW",
                    target.amount(), balance, "카드", NOW.atZone(ZONE).toOffsetDateTime());
        });
        assertError(ErrorCode.PAYMENT_GATEWAY_ERROR,
                () -> paymentService.cancel(memberId, type, target.id(), CANCEL_REQUEST));
        when(gateway.find("payment-key")).thenReturn(approved(target, target.amount()));
        assertThat(paymentService.getPayment(memberId, type, target.id()).status()).isEqualTo(PaymentStatus.CANCEL_UNKNOWN);
        releaseUnpaidResources();
        assertResources(target, true);

        when(gateway.find("payment-key")).thenReturn(cancelled(target));
        paymentService.recoverUnresolvedPayments();
        paymentService.recoverUnresolvedPayments();

        assertThat(paymentService.getPayment(memberId, type, target.id()).status()).isEqualTo(PaymentStatus.CANCELLED);
        assertResources(target, false);
        verify(gateway, times(1)).cancel(anyString(), anyString(), isNull(), anyString());
    }

    @Test
    void cancellationRetryReusesTheOriginalReasonAndIdempotencyKey() {
        Target target = paidTarget(PaymentTargetType.STORE_ORDER);
        when(gateway.cancel(anyString(), anyString(), isNull(), anyString()))
                .thenThrow(new PaymentGateway.Failure(false)).thenReturn(cancelled(target));
        assertThrows(CustomException.class, () -> paymentService.cancel(memberId, target.type(), target.id(), CANCEL_REQUEST));
        assertResources(target, true);

        var result = paymentService.cancel(memberId, target.type(), target.id(), new PaymentCancelReqDTO("재요청"));

        assertThat(result.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(result.cancelReason()).isEqualTo("단순 변심");
        assertResources(target, false);
        verify(gateway, times(2)).cancel("payment-key", "단순 변심", null, "cancel-" + target.orderId());
    }

    @Test
    void paidReservationCannotBeCancelledAfterScreeningStarts() {
        Target target = paidTarget(PaymentTargetType.RESERVATION);
        setTime(NOW.plusDays(1));

        assertError(ErrorCode.RESERVATION_CANCELLATION_CLOSED,
                () -> paymentService.cancel(memberId, target.type(), target.id(), CANCEL_REQUEST));

        assertResources(target, true);
        verify(gateway, never()).cancel(anyString(), anyString(), any(), anyString());
    }

    @Test
    void confirmedCancellationRecoversAfterLocalStockUpdateFails() {
        Target target = paidTarget(PaymentTargetType.STORE_ORDER);
        when(gateway.cancel(anyString(), anyString(), isNull(), anyString())).thenReturn(cancelled(target));
        jdbc.update("update theater_stock set quantity = ? where theater_id = ?", Integer.MAX_VALUE, theaterId);
        assertThrows(CustomException.class, () -> paymentService.cancel(memberId, target.type(), target.id(), CANCEL_REQUEST));
        assertThat(targetStatus(target)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select status from payment where target_type = 'STORE_ORDER' and target_id = ?",
                String.class, target.id())).isEqualTo("CANCEL_PROCESSING");
        jdbc.update("update theater_stock set quantity = 4 where theater_id = ?", theaterId);
        when(gateway.find("payment-key")).thenReturn(cancelled(target));

        paymentService.recoverUnresolvedPayments();

        assertThat(targetStatus(target)).isEqualTo("CANCELLED");
        assertResources(target, false);
        verify(gateway, times(1)).cancel(anyString(), anyString(), isNull(), anyString());
    }

    // 부분 취소
    @ParameterizedTest
    @EnumSource(PaymentTargetType.class)
    void concurrentPartialCancellationUsesOriginalPriceAndRestoresResourcesOnce(PaymentTargetType type) throws Exception {
        var fixture = paidPartialTarget(type);
        Target target = fixture.target();
        var first = fixture.request(0);
        long amount = fixture.price(0);
        when(gateway.cancel(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(partiallyCancelled(target, target.amount() - amount));
        jdbc.update("update product set price = 99999 where product_id = ?", productId);
        jdbc.update("update screening_seat set price = 99999 where screening_id = ?", screeningId);

        var results = runTogether(5, () -> paymentService.cancelPartially(memberId, type, target.id(), first));

        assertThat(results.stream().filter(Objects::isNull).count()).isGreaterThanOrEqualTo(1);
        assertThat(results.stream().filter(Objects::nonNull)).containsOnly(ErrorCode.PAYMENT_PENDING);
        assertThat(paymentService.cancelPartially(memberId, type, target.id(), first).balanceAmount()).isEqualTo(target.amount() - amount);
        assertThat(targetStatus(target)).isEqualTo("PARTIALLY_CANCELLED");
        assertPartialResources(target, 1);
        verify(gateway, times(1)).cancel("payment-key", first.cancelReason(), amount, partialKey(target, first));
        var changed = new PaymentPartialCancelReqDTO(first.requestId(), "다른 내용", first.items(), first.screeningSeatIds());
        assertError(ErrorCode.PAYMENT_CANCEL_REQUEST_CONFLICT,
                () -> paymentService.cancelPartially(memberId, type, target.id(), changed));
        var invalid = type == PaymentTargetType.STORE_ORDER
                ? new PaymentPartialCancelReqDTO(UUID.randomUUID(), "초과 수량", List.of(new PaymentPartialCancelReqDTO.Item(productId, 3)), null)
                : fixture.request(0);
        assertError(ErrorCode.PAYMENT_CANCEL_INVALID,
                () -> paymentService.cancelPartially(memberId, type, target.id(), invalid));
    }

    @ParameterizedTest
    @CsvSource({"STORE_ORDER, true", "STORE_ORDER, false", "RESERVATION, true", "RESERVATION, false"})
    void lastCancellationClosesPaymentAndRestoresRemainingResources(PaymentTargetType type, boolean finishWithFullCancel) {
        var fixture = paidPartialTarget(type);
        Target target = fixture.target();
        long remaining = target.amount();
        for (int index = 0; index < 2; index++) {
            remaining -= fixture.price(index);
            when(gateway.cancel(anyString(), anyString(), anyLong(), anyString()))
                    .thenReturn(partiallyCancelled(target, remaining));
            assertThat(paymentService.cancelPartially(memberId, type, target.id(), fixture.request(index))
                    .balanceAmount()).isEqualTo(remaining);
        }
        if (type == PaymentTargetType.RESERVATION) {
            reservationService.createReservation(memberId, new ReservationReqDTO.CreateReservationDTO(screeningId, List.of(seatId)));
        }

        PaymentResDTO result;
        if (finishWithFullCancel) {
            when(gateway.cancel(anyString(), anyString(), isNull(), anyString())).thenThrow(new PaymentGateway.Failure(false));
            assertError(ErrorCode.PAYMENT_GATEWAY_ERROR,
                    () -> paymentService.cancel(memberId, type, target.id(), CANCEL_REQUEST));
            assertPartialResources(target, 2);
            when(gateway.find("payment-key")).thenReturn(partiallyCancelled(target, 0));
            result = paymentService.getPayment(memberId, type, target.id());
        } else {
            when(gateway.cancel(anyString(), anyString(), anyLong(), anyString())).thenReturn(partiallyCancelled(target, 0));
            result = paymentService.cancelPartially(memberId, type, target.id(), fixture.request(2));
        }
        assertThat(result.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(result.balanceAmount()).isZero();
        assertPartialResources(target, 3);
        if (type == PaymentTargetType.RESERVATION) {
            assertThat(count("select count(*) from screening_seat where screening_seat_id = ? and reservation_id is not null", seatId)).isOne();
        }
    }

    @ParameterizedTest
    @EnumSource(PaymentTargetType.class)
    void uncertainPartialCancellationBlocksOtherRequestsAndRetriesWithTheSameKey(PaymentTargetType type) {
        var fixture = paidPartialTarget(type);
        Target target = fixture.target();
        var request = fixture.request(0);
        long amount = fixture.price(0);
        when(gateway.cancel(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new PaymentGateway.Failure(false)).thenReturn(partiallyCancelled(target, target.amount() - amount));
        assertThrows(CustomException.class, () -> paymentService.cancelPartially(memberId, type, target.id(), request));
        when(gateway.find("payment-key")).thenReturn(approved(target, target.amount()));
        assertThat(paymentService.getPayment(memberId, type, target.id()).status()).isEqualTo(PaymentStatus.CANCEL_UNKNOWN);
        assertPartialResources(target, 0);
        assertError(ErrorCode.PAYMENT_PENDING,
                () -> paymentService.cancelPartially(memberId, type, target.id(), fixture.request(1)));
        assertError(ErrorCode.PAYMENT_PENDING,
                () -> paymentService.cancel(memberId, type, target.id(), CANCEL_REQUEST));

        if (type == PaymentTargetType.RESERVATION) setTime(NOW.plusDays(1));
        assertThat(paymentService.cancelPartially(memberId, type, target.id(), request).balanceAmount())
                .isEqualTo(target.amount() - amount);
        assertPartialResources(target, 1);
        verify(gateway, times(2)).cancel("payment-key", request.cancelReason(), amount, partialKey(target, request));
    }

    @ParameterizedTest
    @EnumSource(PaymentTargetType.class)
    void rejectedPartialCancellationIsRecoveredAndAllowsAnotherRequest(PaymentTargetType type) {
        var fixture = paidPartialTarget(type);
        Target target = fixture.target();
        var first = fixture.request(0);
        var next = fixture.request(1);
        when(gateway.cancel(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new PaymentGateway.Failure(true))
                .thenReturn(partiallyCancelled(target, target.amount() - fixture.price(1)));
        when(gateway.find("payment-key")).thenThrow(new PaymentGateway.Failure(false))
                .thenReturn(approved(target, target.amount()));

        assertThrows(CustomException.class, () -> paymentService.cancelPartially(memberId, type, target.id(), first));
        paymentService.recoverUnresolvedPayments();
        assertPartialResources(target, 0);
        assertError(ErrorCode.PAYMENT_CANCELLATION_REJECTED,
                () -> paymentService.cancelPartially(memberId, type, target.id(), first));
        assertThat(paymentService.cancelPartially(memberId, type, target.id(), next).status())
                .isEqualTo(PaymentStatus.PARTIALLY_CANCELLED);
        verify(gateway, times(2)).cancel(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void lateLookupFromPreviousCancellationCannotChangeTheNextRequest() throws Exception {
        var fixture = paidPartialTarget(PaymentTargetType.STORE_ORDER);
        Target target = fixture.target();
        var first = fixture.request(0);
        var firstResult = partiallyCancelled(target, target.amount() - fixture.price(0));
        when(gateway.cancel(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new PaymentGateway.Failure(false)).thenReturn(firstResult);
        assertThrows(CustomException.class, () -> paymentService.cancelPartially(memberId, target.type(), target.id(), first));
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(gateway.find("payment-key")).thenAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return firstResult;
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var lookup = executor.submit(() -> paymentService.getPayment(memberId, target.type(), target.id()));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            paymentService.cancelPartially(memberId, target.type(), target.id(), first);
            when(gateway.cancel(anyString(), anyString(), anyLong(), anyString())).thenAnswer(invocation -> {
                release.countDown();
                assertThat(lookup.get(5, TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.CANCEL_PROCESSING);
                throw new PaymentGateway.Failure(false);
            });
            assertThrows(CustomException.class,
                    () -> paymentService.cancelPartially(memberId, target.type(), target.id(), fixture.request(1)));
        } finally {
            release.countDown();
        }
        when(gateway.find("payment-key")).thenReturn(partiallyCancelled(target, target.amount() - fixture.price(0) - fixture.price(1)));
        paymentService.recoverUnresolvedPayments();
        assertPartialResources(target, 2);
    }

    private PartialTarget paidPartialTarget(PaymentTargetType type) {
        if (type == PaymentTargetType.STORE_ORDER) {
            return new PartialTarget(approve(orderTarget(3)), Collections.nCopies(3, new PricedItem(productId, PRODUCT_PRICE)));
        }
        var seats = List.of(new PricedItem(seatId, FIRST_SEAT_PRICE), insertSeat(2, 10_000), insertSeat(3, 8_000));
        var target = approve(reservationTarget(seats.stream().map(PricedItem::id).toList()));
        return new PartialTarget(target, seats);
    }

    private PricedItem insertSeat(int column, int price) {
        long id = insert("insert into screening_seat (screening_id, row_no, column_no, price) values (?, 1, ?, ?)",
                screeningId, column, price);
        return new PricedItem(id, price);
    }

    private record PricedItem(long id, long price) {
    }

    private record PartialTarget(Target target, List<PricedItem> items) {
        PaymentPartialCancelReqDTO request(int index) {
            long id = items.get(index).id();
            boolean storeOrder = target.type() == PaymentTargetType.STORE_ORDER;
            return new PaymentPartialCancelReqDTO(UUID.randomUUID(), "일부 취소",
                    storeOrder ? List.of(new PaymentPartialCancelReqDTO.Item(id, 1)) : List.of(),
                    storeOrder ? List.of() : List.of(id));
        }

        long price(int index) {
            return items.get(index).price();
        }
    }

    private String partialKey(Target target, PaymentPartialCancelReqDTO request) {
        return "cancel-" + target.orderId() + "-" + request.requestId();
    }

    private PaymentGateway.Result partiallyCancelled(Target target, long balance) {
        return new PaymentGateway.Result("payment-key", target.orderId(), "PARTIAL_CANCELED",
                "KRW", target.amount(), balance, "카드", NOW.atZone(ZONE).toOffsetDateTime());
    }

    private void assertPartialResources(Target target, int cancelled) {
        if (target.type() == PaymentTargetType.STORE_ORDER) {
            assertThat(count("select cancelled_quantity from store_order_item where order_id = ?", target.id())).isEqualTo(cancelled);
            assertThat(count("select quantity from theater_stock where theater_id = ?", theaterId)).isEqualTo(3 + cancelled);
        } else {
            assertThat(count("select count(*) from reservation_seat where reservation_id = ? and cancelled = true", target.id())).isEqualTo(cancelled);
            assertThat(count("select count(*) from screening_seat where reservation_id = ?", target.id())).isEqualTo(3 - cancelled);
        }
    }

    private Target paidTarget(PaymentTargetType type) {
        return approve(paymentTarget(type));
    }

    private Target approve(Target target) {
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(approved(target, target.amount()));
        paymentService.confirm(memberId, target.type(), target.id(), paymentRequest(target));
        return target;
    }

    private PaymentGateway.Result cancelled(Target target) {
        return new PaymentGateway.Result("payment-key", target.orderId(), "CANCELED", "KRW", target.amount(), 0L,
                "카드", NOW.atZone(ZONE).toOffsetDateTime());
    }

    private Target paymentTarget(PaymentTargetType type) {
        return type == PaymentTargetType.STORE_ORDER ? orderTarget(2) : reservationTarget(List.of(seatId));
    }

    private Target orderTarget(int quantity) {
        var order = createOrder(quantity);
        return new Target(PaymentTargetType.STORE_ORDER, order.orderId(), order.paymentOrderId(), order.totalPrice());
    }

    private Target reservationTarget(List<Long> seatIds) {
        var reservation = reservationService.createReservation(memberId,
                new ReservationReqDTO.CreateReservationDTO(screeningId, seatIds));
        return new Target(PaymentTargetType.RESERVATION, reservation.reservationId(), reservation.paymentOrderId(), reservation.totalPrice());
    }

    private PaymentReqDTO paymentRequest(Target target) {
        return new PaymentReqDTO("payment-key", target.orderId(), target.amount());
    }

    private PaymentGateway.Result approved(Target target, long amount) {
        return new PaymentGateway.Result("payment-key", target.orderId(), "DONE", "KRW", amount, amount,
                "카드", NOW.atZone(ZONE).toOffsetDateTime());
    }

    private void releaseUnpaidResources() {
        storeService.releaseUnpaidOrderStock();
        reservationService.releaseUnpaidSeats();
    }

    private void assertResources(Target target, boolean held) {
        if (target.type() == PaymentTargetType.STORE_ORDER) {
            assertThat(count("select quantity from theater_stock where theater_id = ?", theaterId)).isEqualTo(held ? 4 : 6);
        } else {
            assertThat(count("select count(*) from screening_seat where screening_seat_id = ? and reservation_id is not null", seatId))
                    .isEqualTo(held ? 1 : 0);
        }
    }

    private String targetStatus(Target target) {
        String sql = target.type() == PaymentTargetType.STORE_ORDER
                ? "select status from store_order where store_order_id = ?"
                : "select status from reservation where reservation_id = ?";
        return jdbc.queryForObject(sql, String.class, target.id());
    }

    private record Target(PaymentTargetType type, Long id, String orderId, long amount) {
    }

    // 각 요청은 서비스 프록시를 통과해 독립 트랜잭션으로 커밋한다.
    private List<ErrorCode> runTogether(int workers, Callable<?> action) throws Exception {
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("동시 요청 시작 시간 초과");
                    try {
                        action.call();
                        return null;
                    } catch (CustomException error) {
                        return error.getErrorCode();
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<ErrorCode> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(15, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
        }
    }

    private void setTime(LocalDateTime time) {
        when(clock.instant()).thenReturn(time.atZone(ZONE).toInstant());
    }

    private StoreOrderResDTO createOrder(int quantity) {
        var item = new StoreOrderReqDTO.OrderItemDTO(productId, quantity);
        return storeService.createOrder(theaterId, memberId, new StoreOrderReqDTO.CreateOrderDTO(List.of(item)));
    }

    private void assertError(ErrorCode expected, Executable request) {
        assertThat(assertThrows(CustomException.class, request).getErrorCode()).isEqualTo(expected);
    }

    private void assertResults(List<ErrorCode> results, int successes, ErrorCode failure) {
        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(successes);
        assertThat(results.stream().filter(Objects::nonNull)).containsOnly(failure);
    }

    private long insert(String sql, Object... parameters) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private long count(String sql, Object... parameters) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, parameters));
    }

    @AfterEach
    void cleanUp() {
        Long movieId = jdbc.queryForObject("select movie_id from screening where screening_id = ?", Long.class, screeningId);
        String cancellations = """
                select c.payment_cancellation_id
                from payment_cancellation c join payment p on c.payment_id = p.payment_id
                where (p.target_type = 'STORE_ORDER' and p.target_id in (
                    select store_order_id from store_order where theater_id = ?))
                or (p.target_type = 'RESERVATION' and p.target_id in (
                    select reservation_id from reservation where member_id = ?))
                """;
        jdbc.update("delete from payment_cancellation_item where payment_cancellation_id in (" + cancellations + ")", theaterId, memberId);
        jdbc.update("delete from payment_cancellation where payment_cancellation_id in (select payment_cancellation_id from ("
                + cancellations + ") matched)", theaterId, memberId);
        jdbc.update("delete from payment where target_type = 'STORE_ORDER' and target_id in (select store_order_id from store_order where theater_id = ?)", theaterId);
        jdbc.update("delete from payment where target_type = 'RESERVATION' and target_id in (select reservation_id from reservation where member_id = ?)", memberId);
        jdbc.update("delete from store_order_item where product_id = ?", productId);
        jdbc.update("delete from store_order where theater_id = ?", theaterId);
        jdbc.update("delete from theater_stock where theater_id = ?", theaterId);
        jdbc.update("delete from product where product_id = ?", productId);
        jdbc.update("delete from reservation_seat where reservation_id in (select reservation_id from reservation where member_id = ?)", memberId);
        jdbc.update("update screening_seat set reservation_id = null where screening_id = ?", screeningId);
        jdbc.update("delete from reservation where member_id = ?", memberId);
        jdbc.update("delete from screening_seat where screening_id in (select screening_id from screening where auditorium_id = ?)", auditoriumId);
        jdbc.update("delete from screening where auditorium_id = ?", auditoriumId);
        jdbc.update("delete from auditorium where auditorium_id = ?", auditoriumId);
        jdbc.update("delete from movie where movie_id = ?", movieId);
        jdbc.update("delete from theater where theater_id = ?", theaterId);
        jdbc.update("delete from member where member_id = ?", memberId);
    }
}
