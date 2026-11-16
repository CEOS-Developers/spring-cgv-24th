package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.enums.OrderStatus;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import com.ceos24.cgv.domain.payment.service.PaymentFacade;
import com.ceos24.cgv.domain.payment.service.PaymentService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRecoveryIntegrationTest extends ConcurrencyTestSupport {

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ReservationService reservationService;

    @Test
    void 보상_취소_응답이_유실되면_결과_불명으로_남고_대사에서_토스_취소_완료를_보고_한_번만_복구한다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item item = saveItem(5_000);
        saveStock(theater, item, 1);
        Order order = saveOrder(user, theater, item, 2);
        Payment payment = compensationResponseLost(user, order);

        when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
                .thenReturn(tossResponse("CANCELED", payment, 0, payment.getAmount()));
        PaymentResponse recovered = paymentFacade.reconcile(user.getId(), payment.getId());

        assertThat(recovered.status()).isEqualTo(PaymentStatus.CANCELED);
        Payment result = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.getBalanceAmount()).isZero();
        assertThat(result.getPendingCancelAmount()).isZero();
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(stock(theater, item)).isEqualTo(1);
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(payment.getId())).singleElement().satisfies(cancel -> {
            assertThat(cancel.getIdempotencyKey()).isEqualTo(payment.getCompensationIdempotencyKey());
            assertThat(cancel.getCancelAmount()).isEqualTo(10_000);
            assertThat(cancel.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
        });

        assertThatThrownBy(() -> paymentFacade.reconcile(user.getId(), payment.getId()))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT409_2"));
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(payment.getId())).hasSize(1);
        verify(tossPaymentClient, times(1)).cancel(any(), any(), any(), any());
    }

    @Test
    void 결과_불명_결제를_동시에_대사해도_한_요청만_처리하고_보상_이력은_한_번만_남는다() throws InterruptedException {
        User user = saveUser();
        Theater theater = saveTheater();
        Item item = saveItem(5_000);
        saveStock(theater, item, 1);
        Order order = saveOrder(user, theater, item, 2);
        Payment payment = compensationResponseLost(user, order);
        when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenAnswer(invocation -> {
            Thread.sleep(100);
            return tossResponse("CANCELED", payment, 0, payment.getAmount());
        });

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> paymentFacade.reconcile(user.getId(), payment.getId()));
        }
        List<Throwable> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(1);
        assertThat(failureCount(results, "PAYMENT409_2")).isEqualTo(9);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(payment.getId())).hasSize(1);
        verify(tossPaymentClient, times(1)).getByOrderId(payment.getTossOrderId());
    }

    @Test
    void 승인_시작_직후_서버가_멈춘_예매_결제는_제한_시간이_지나야_복구되고_토스_승인이_확인되면_예매를_확정한다() {
        Long userId = saveUser().getId();
        Payment payment = reservationPayment(userId, saveSchedule(1));
        startApprovalThenCrash(userId, payment);

        paymentFacade.recoverStalePayments();
        assertThatThrownBy(() -> paymentFacade.reconcile(userId, payment.getId()))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT409_2"));
        verify(tossPaymentClient, never()).getByOrderId(any());
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);

        makeStale(payment.getId());
        when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
                .thenReturn(tossResponse("DONE", payment, payment.getAmount(), 0));
        paymentFacade.recoverStalePayments();

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(reservationStatus(payment)).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservationId(payment))).hasSize(1);
        verify(tossPaymentClient, never()).confirm(any(), any(), anyInt(), any());
    }

    @Test
    void 승인되지_않은_채_만료된_결제는_자동_복구에서_실패_처리하고_좌석을_반환한다() {
        Schedule schedule = saveSchedule(1);
        Long userId = saveUser().getId();
        Payment payment = reservationPayment(userId, schedule);
        startApprovalThenCrash(userId, payment);
        makeStale(payment.getId());
        when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
                .thenReturn(tossResponse("EXPIRED", payment, payment.getAmount(), 0));

        paymentFacade.recoverStalePayments();

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reservationStatus(payment)).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservationId(payment))).isEmpty();
        Long nextReservationId = reservationService.create(saveUser().getId(),
                new ReservationCreateRequest(schedule.getId(), List.of(seatId())));
        assertThat(reservationRepository.findById(nextReservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void 토스_결과가_아직_불확실하면_좌석을_유지한_채_결과_불명으로_두고_다음_복구에서_확정한다() {
        Long userId = saveUser().getId();
        Payment payment = reservationPayment(userId, saveSchedule(1));
        startApprovalThenCrash(userId, payment);
        makeStale(payment.getId());
        when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
                .thenReturn(tossResponse("IN_PROGRESS", payment, payment.getAmount(), 0))
                .thenReturn(tossResponse("EXPIRED", payment, payment.getAmount(), 0));

        paymentFacade.recoverStalePayments();

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(reservationStatus(payment)).isEqualTo(ReservationStatus.PAYING);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservationId(payment))).hasSize(1);

        paymentFacade.recoverStalePayments();
        verify(tossPaymentClient, times(1)).getByOrderId(payment.getTossOrderId());

        makeStale(payment.getId());
        paymentFacade.recoverStalePayments();

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reservationStatus(payment)).isEqualTo(ReservationStatus.CANCELLED);
        verify(tossPaymentClient, times(2)).getByOrderId(payment.getTossOrderId());
        verify(tossPaymentClient, never()).confirm(any(), any(), anyInt(), any());
    }

    @Test
    void 멈춘_매점_결제를_자동_복구와_사용자_대사가_동시에_처리해도_재고는_한_번만_차감된다() throws InterruptedException {
        User user = saveUser();
        Theater theater = saveTheater();
        Item item = saveItem(5_000);
        saveStock(theater, item, 10);
        Order order = saveOrder(user, theater, item, 2);
        Payment payment = paymentRepository.save(Payment.forOrder(order));
        startApprovalThenCrash(user.getId(), payment);
        makeStale(payment.getId());
        when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenAnswer(invocation -> {
            Thread.sleep(100);
            return tossResponse("DONE", payment, payment.getAmount(), 0);
        });

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> paymentFacade.recoverStalePayments());
            tasks.add(() -> paymentFacade.reconcile(user.getId(), payment.getId()));
        }
        List<Throwable> results = runConcurrently(tasks);

        assertThat(failureCounts(results).keySet()).allMatch("PAYMENT409_2"::equals);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(stock(theater, item)).isEqualTo(8);
        verify(tossPaymentClient, times(1)).getByOrderId(payment.getTossOrderId());
        verify(tossPaymentClient, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void 결과_불명_재승인이_거절돼도_토스에서_승인된_것이_확인되면_예매를_확정한다() {
        User user = saveUser();
        Payment payment = reservationPayment(user.getId(), saveSchedule(1));
        when(tossPaymentClient.confirm(any(), any(), anyInt(), any())).thenThrow(TossPaymentException.unknown("Read timed out"));
        assertThatThrownBy(() -> confirm(user, payment, "pk-" + payment.getId())).isInstanceOf(TossPaymentException.class);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);

        doThrow(TossPaymentException.rejected("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."))
                .when(tossPaymentClient).confirm(any(), any(), anyInt(), any());
        when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
                .thenReturn(tossResponse("DONE", payment, payment.getAmount(), 0));
        PaymentResponse response = confirm(user, payment, "wrong-payment-key");

        assertThat(response.status()).isEqualTo(PaymentStatus.DONE);
        Payment result = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(result.getPaymentKey()).isEqualTo("pk-" + payment.getId());
        assertThat(reservationStatus(payment)).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservationId(payment))).hasSize(1);
        verify(tossPaymentClient, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void 결과_불명_재승인이_거절되고_토스에서도_승인_실패로_확인되면_실패를_확정하고_좌석을_반환한다() {
        User user = saveUser();
        Payment payment = reservationPayment(user.getId(), saveSchedule(1));
        when(tossPaymentClient.confirm(any(), any(), anyInt(), any())).thenThrow(TossPaymentException.unknown("Read timed out"));
        assertThatThrownBy(() -> confirm(user, payment, "pk-" + payment.getId())).isInstanceOf(TossPaymentException.class);
        assertThat(reservationStatus(payment)).isEqualTo(ReservationStatus.PAYING);

        TossPaymentException rejection = TossPaymentException.rejected("REJECT_CARD_COMPANY", "카드사에서 거절했습니다.");
        doThrow(rejection).when(tossPaymentClient).confirm(any(), any(), anyInt(), any());
        when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
                .thenReturn(tossResponse("ABORTED", payment, payment.getAmount(), 0));

        assertThatThrownBy(() -> confirm(user, payment, "pk-" + payment.getId())).isSameAs(rejection);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reservationStatus(payment)).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservationId(payment))).isEmpty();
    }

    @Test
    void 결제_승인과_결제_전_예매_취소가_동시에_와도_한쪽만_반영되고_보상_취소가_필요한_상태가_생기지_않는다() throws Exception {
        Schedule schedule = saveSchedule(5);
        when(tossPaymentClient.confirm(any(), any(), anyInt(), any())).thenAnswer(invocation -> {
            Payment target = paymentRepository.findByTossOrderId(invocation.getArgument(1)).orElseThrow();
            return tossResponse("DONE", target, target.getAmount(), 0);
        });

        for (Seat seat : seatRepository.findAll()) {
            User user = saveUser();
            Long reservationId = reservationService.create(user.getId(),
                    new ReservationCreateRequest(schedule.getId(), List.of(seat.getId())));
            Payment payment = paymentRepository.findByReservationId(reservationId).orElseThrow();

            List<Throwable> results = runConcurrently(List.of(
                    () -> reservationService.cancel(reservationId, user.getId()),
                    () -> confirm(user, payment, "pk-" + payment.getId())));

            assertThat(successCount(results)).isEqualTo(1);
            ReservationStatus reservationStatus = reservationRepository.findById(reservationId).orElseThrow().getStatus();
            PaymentStatus paymentStatus = paymentRepository.findById(payment.getId()).orElseThrow().getStatus();
            if (results.get(0) == null) {
                assertThat(errorCode(results.get(1))).isEqualTo("RESERVATION409_3");
                assertThat(reservationStatus).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(paymentStatus).isEqualTo(PaymentStatus.EXPIRED);
                assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservationId)).isEmpty();
            } else {
                assertThat(errorCode(results.get(0))).isIn("RESERVATION409_5", "RESERVATION409_4");
                assertThat(reservationStatus).isEqualTo(ReservationStatus.RESERVED);
                assertThat(paymentStatus).isEqualTo(PaymentStatus.DONE);
                assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservationId)).hasSize(1);
            }
        }
        verify(tossPaymentClient, never()).cancel(any(), any(), any(), any());
    }

    private PaymentResponse confirm(User user, Payment payment, String paymentKey) {
        return paymentFacade.confirm(user.getId(),
                new PaymentConfirmRequest(paymentKey, payment.getTossOrderId(), payment.getAmount()));
    }

    // 승인 후 재고 부족으로 보상 취소를 보냈는데 응답이 유실된 상태를 만든다 (토스에서는 실제로 취소됨)
    private Payment compensationResponseLost(User user, Order order) {
        Payment payment = paymentRepository.save(Payment.forOrder(order));
        when(tossPaymentClient.confirm(any(), any(), anyInt(), any()))
                .thenReturn(tossResponse("DONE", payment, payment.getAmount(), 0));
        when(tossPaymentClient.cancel(any(), any(), any(), any())).thenThrow(TossPaymentException.unknown("Read timed out"));

        assertThatThrownBy(() -> paymentFacade.confirm(user.getId(),
                new PaymentConfirmRequest("pk-" + payment.getId(), payment.getTossOrderId(), payment.getAmount())))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("STOCK409"));
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        return payment;
    }

    private Payment reservationPayment(Long userId, Schedule schedule) {
        Long reservationId = reservationService.create(userId, new ReservationCreateRequest(schedule.getId(), List.of(seatId())));
        return paymentRepository.findByReservationId(reservationId).orElseThrow();
    }

    private void startApprovalThenCrash(Long userId, Payment payment) {
        paymentService.startApproval(userId,
                new PaymentConfirmRequest("pk-" + payment.getId(), payment.getTossOrderId(), payment.getAmount()));
    }

    private void makeStale(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        ReflectionTestUtils.setField(payment, "processingStartedAt",
                Instant.now().minus(Payment.PROCESSING_TIMEOUT).minusSeconds(60));
        paymentRepository.save(payment);
    }

    private Long seatId() {
        return seatRepository.findAll().stream().map(Seat::getId).sorted().findFirst().orElseThrow();
    }

    private Long reservationId(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow().getReservation().getId();
    }

    private ReservationStatus reservationStatus(Payment payment) {
        return reservationRepository.findById(reservationId(payment)).orElseThrow().getStatus();
    }

    private int stock(Theater theater, Item item) {
        return stockRepository.findByTheaterIdAndItemId(theater.getId(), item.getId()).orElseThrow().getQuantity();
    }
}
