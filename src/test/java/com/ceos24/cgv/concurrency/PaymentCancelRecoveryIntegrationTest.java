package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.enums.OrderStatus;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import com.ceos24.cgv.domain.payment.service.PaymentFacade;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentCancelRecoveryIntegrationTest extends ConcurrencyTestSupport {

    private static final String REASON = "단순 변심";

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private ReservationService reservationService;

    private User user;
    private Theater theater;
    private Item item;

    @Test
    void 처리_중인_취소가_남아_있으면_다른_취소가_성공해도_전액_취소로_처리하지_않고_실패한_금액은_다시_취소할_수_있다()
            throws Exception {
        Payment payment = paidOrderPayment();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        doAnswer(invocation -> {
            Integer amount = invocation.getArgument(2);
            if (amount == 4_000) {
                entered.countDown();
                proceed.await(10, TimeUnit.SECONDS);
                throw TossPaymentException.rejected("NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액입니다.");
            }
            return tossResponse("PARTIAL_CANCELED", payment, 4_000, 6_000);
        }).when(tossPaymentClient).cancel(any(), any(), any(), any());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<PaymentResponse> slow = executor.submit(() -> cancel(payment, "cancel-4000", 4_000, REASON));
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        cancel(payment, "cancel-6000", 6_000, REASON);

        Payment inFlight = payment(payment);
        assertThat(inFlight.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        assertThat(inFlight.getBalanceAmount()).isEqualTo(4_000);
        assertThat(inFlight.getPendingCancelAmount()).isEqualTo(4_000);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.PAID);
        assertThat(stock()).isEqualTo(8);

        proceed.countDown();
        assertThatThrownBy(slow::get).hasCauseInstanceOf(TossPaymentException.class);
        executor.shutdown();

        Payment afterFailure = payment(payment);
        assertThat(afterFailure.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        assertThat(afterFailure.getBalanceAmount()).isEqualTo(4_000);
        assertThat(afterFailure.getPendingCancelAmount()).isZero();
        assertThat(afterFailure.getCancelableAmount()).isEqualTo(4_000);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.PAID);
        assertThat(stock()).isEqualTo(8);
        assertThat(cancelStatusByAmount(payment))
                .containsEntry(6_000, PaymentCancelStatus.DONE)
                .containsEntry(4_000, PaymentCancelStatus.FAILED);

        doReturn(tossResponse("CANCELED", payment, 0, 4_000)).when(tossPaymentClient).cancel(any(), any(), any(), any());
        cancel(payment, "cancel-rest", null, REASON);

        Payment result = payment(payment);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.getBalanceAmount()).isZero();
        assertThat(result.getPendingCancelAmount()).isZero();
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.CANCELED);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void 결과_불명_취소를_재시도했는데_거절되고_토스에_취소_흔적이_없으면_예약_금액을_한_번만_복구한다() {
        Payment payment = paidOrderPayment();
        doThrow(TossPaymentException.unknown("Read timed out")).when(tossPaymentClient).cancel(any(), any(), any(), any());
        assertThatThrownBy(() -> cancel(payment, "cancel-1", 3_000, REASON))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT502"));
        assertThat(payment(payment).getPendingCancelAmount()).isEqualTo(3_000);
        assertThat(payment(payment).getCancelableAmount()).isEqualTo(7_000);

        doThrow(TossPaymentException.rejected("NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액입니다."))
                .when(tossPaymentClient).cancel(any(), any(), any(), any());
        when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenReturn(tossResponse("DONE", payment, 10_000, 0));
        assertThatThrownBy(() -> cancel(payment, "cancel-1", 3_000, REASON))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT400"));

        Payment afterFailure = payment(payment);
        assertThat(afterFailure.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(afterFailure.getBalanceAmount()).isEqualTo(10_000);
        assertThat(afterFailure.getPendingCancelAmount()).isZero();
        assertThat(cancelStatusByAmount(payment)).containsEntry(3_000, PaymentCancelStatus.FAILED);

        assertThatThrownBy(() -> cancel(payment, "cancel-1", 3_000, REASON))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT409_6"));
        assertThat(payment(payment).getPendingCancelAmount()).isZero();
        assertThat(payment(payment).getCancelableAmount()).isEqualTo(10_000);

        doReturn(tossResponse("PARTIAL_CANCELED", payment, 7_000, 3_000)).when(tossPaymentClient).cancel(any(), any(), any(), any());
        cancel(payment, "cancel-2", 3_000, REASON);

        assertThat(payment(payment).getBalanceAmount()).isEqualTo(7_000);
        assertThat(payment(payment).getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.PAID);
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void 결과_불명_취소_재시도가_거절됐지만_토스에서_실제로_취소된_것이_확인되면_완료로_반영한다() {
        Payment payment = paidOrderPayment();
        doThrow(TossPaymentException.unknown("Read timed out")).when(tossPaymentClient).cancel(any(), any(), any(), any());
        assertThatThrownBy(() -> cancel(payment, "cancel-1", 3_000, REASON)).isInstanceOf(TossPaymentException.class);

        doThrow(TossPaymentException.rejected("NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액입니다."))
                .when(tossPaymentClient).cancel(any(), any(), any(), any());
        when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
                .thenReturn(tossResponse("PARTIAL_CANCELED", payment, 7_000, 3_000));
        PaymentResponse response = cancel(payment, "cancel-1", 3_000, REASON);

        assertThat(response.status()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        Payment result = payment(payment);
        assertThat(result.getBalanceAmount()).isEqualTo(7_000);
        assertThat(result.getPendingCancelAmount()).isZero();
        assertThat(cancelStatusByAmount(payment)).containsEntry(3_000, PaymentCancelStatus.DONE);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.PAID);
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void 결과_불명_전액_취소를_재시도해_성공하면_주문_취소와_재고_복구를_한_번만_반영한다() {
        Payment payment = paidOrderPayment();
        doThrow(TossPaymentException.unknown("Read timed out")).when(tossPaymentClient).cancel(any(), any(), any(), any());
        assertThatThrownBy(() -> cancel(payment, "cancel-full", null, REASON)).isInstanceOf(TossPaymentException.class);
        assertThat(payment(payment).getPendingCancelAmount()).isEqualTo(10_000);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.PAID);
        assertThat(stock()).isEqualTo(8);

        doReturn(tossResponse("CANCELED", payment, 0, 10_000)).when(tossPaymentClient).cancel(any(), any(), any(), any());
        PaymentResponse response = cancel(payment, "cancel-full", null, REASON);
        PaymentResponse replay = cancel(payment, "cancel-full", null, REASON);

        assertThat(response.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(replay.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment(payment).getBalanceAmount()).isZero();
        assertThat(payment(payment).getPendingCancelAmount()).isZero();
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.CANCELED);
        assertThat(stock()).isEqualTo(10);
        verify(tossPaymentClient, times(2)).cancel(any(), any(), any(), any());
    }

    @Test
    void 같은_멱등키는_최초와_같은_요청일_때만_재시도로_인정한다() {
        Payment payment = paidOrderPayment();
        doReturn(tossResponse("PARTIAL_CANCELED", payment, 7_000, 3_000)).when(tossPaymentClient).cancel(any(), any(), any(), any());

        cancel(payment, "cancel-123", 3_000, REASON);
        PaymentResponse replay = cancel(payment, "cancel-123", 3_000, REASON);

        assertThat(replay.balanceAmount()).isEqualTo(7_000);
        assertThatThrownBy(() -> cancel(payment, "cancel-123", 5_000, REASON))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT409_7"));
        assertThatThrownBy(() -> cancel(payment, "cancel-123", 3_000, "중복 결제"))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT409_7"));
        assertThatThrownBy(() -> cancel(payment, "cancel-123", null, REASON))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT409_7"));

        Payment result = payment(payment);
        assertThat(result.getBalanceAmount()).isEqualTo(7_000);
        assertThat(result.getPendingCancelAmount()).isZero();
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(payment.getId())).hasSize(1);
        verify(tossPaymentClient, times(1)).cancel(any(), any(), any(), any());
    }

    @Test
    void 예매_결제를_전액_취소하면_결제_예매_좌석이_한_번만_정리되고_좌석을_다시_예매할_수_있다() {
        Schedule schedule = saveSchedule(1);
        Long seatId = seatRepository.findAll().stream().map(Seat::getId).findFirst().orElseThrow();
        user = saveUser();
        Long reservationId = reservationService.create(user.getId(), new ReservationCreateRequest(schedule.getId(), List.of(seatId)));
        Payment payment = paymentRepository.findByReservationId(reservationId).orElseThrow();
        when(tossPaymentClient.confirm(any(), any(), anyInt(), any()))
                .thenReturn(tossResponse("DONE", payment, payment.getAmount(), 0));
        paymentFacade.confirm(user.getId(),
                new PaymentConfirmRequest("pk-" + payment.getId(), payment.getTossOrderId(), payment.getAmount()));
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        doReturn(tossResponse("CANCELED", payment, 0, payment.getAmount())).when(tossPaymentClient).cancel(any(), any(), any(), any());

        cancel(payment, "cancel-reservation", null, REASON);
        cancel(payment, "cancel-reservation", null, REASON);

        Payment result = payment(payment);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.getBalanceAmount()).isZero();
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservationId)).isEmpty();
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(payment.getId())).hasSize(1);
        verify(tossPaymentClient, times(1)).cancel(any(), any(), any(), any());
        Long nextReservationId = reservationService.create(saveUser().getId(),
                new ReservationCreateRequest(schedule.getId(), List.of(seatId)));
        assertThat(reservationRepository.findById(nextReservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    // 재고 10개 중 2개(10,000원)를 결제 승인까지 마친 매점 주문 결제
    private Payment paidOrderPayment() {
        user = saveUser();
        theater = saveTheater();
        item = saveItem(5_000);
        saveStock(theater, item, 10);
        Payment payment = paymentRepository.save(Payment.forOrder(saveOrder(user, theater, item, 2)));
        when(tossPaymentClient.confirm(any(), any(), anyInt(), any())).thenReturn(tossResponse("DONE", payment, 10_000, 0));
        paymentFacade.confirm(user.getId(), new PaymentConfirmRequest("pk-" + payment.getId(), payment.getTossOrderId(), 10_000));
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.PAID);
        assertThat(stock()).isEqualTo(8);
        return payment;
    }

    private PaymentResponse cancel(Payment payment, String idempotencyKey, Integer amount, String reason) {
        return paymentFacade.cancel(user.getId(), payment.getId(), idempotencyKey, new PaymentCancelRequest(reason, amount));
    }

    private Payment payment(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private OrderStatus orderStatus(Payment payment) {
        return orderRepository.findById(payment.getOrder().getId()).orElseThrow().getStatus();
    }

    private int stock() {
        return stockRepository.findByTheaterIdAndItemId(theater.getId(), item.getId()).orElseThrow().getQuantity();
    }

    private Map<Integer, PaymentCancelStatus> cancelStatusByAmount(Payment payment) {
        return paymentCancelRepository.findByPaymentIdOrderByIdAsc(payment.getId()).stream()
                .collect(Collectors.toMap(PaymentCancel::getCancelAmount, PaymentCancel::getStatus));
    }
}
