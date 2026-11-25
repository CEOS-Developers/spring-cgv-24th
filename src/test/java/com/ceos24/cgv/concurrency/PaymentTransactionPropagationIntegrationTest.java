package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.entity.OrderItem;
import com.ceos24.cgv.domain.concession.enums.OrderStatus;
import com.ceos24.cgv.domain.concession.service.OrderService;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.service.PaymentFacade;
import com.ceos24.cgv.domain.payment.service.PaymentService;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentTransactionPropagationIntegrationTest extends ConcurrencyTestSupport {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void 트랜잭션_밖에서_주문_결제_후처리를_호출하면_예외가_나고_재고와_주문이_바뀌지_않는다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item item = saveItem(5_000);
        saveStock(theater, item, 10);
        Order pending = saveOrder(user, theater, item, 2);
        Order paid = saveOrder(user, theater, item, 3);
        paid.completePayment();
        orderRepository.save(paid);

        assertThatThrownBy(() -> orderService.completePaidOrder(pending))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> orderService.restoreCanceledOrder(paid))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(stockQuantity(theater, item)).isEqualTo(10);
        assertThat(orderStatus(pending)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderStatus(paid)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 트랜잭션_밖에서_예매_결제_후처리를_호출하면_예외가_나고_예매와_좌석이_바뀌지_않는다() {
        Schedule schedule = saveSchedule(3);
        List<Long> seatIds = seatIds();
        Long pendingId = saveReservation(schedule, seatIds.get(0), ReservationStatus.PENDING_PAYMENT);
        Long payingId = saveReservation(schedule, seatIds.get(1), ReservationStatus.PAYING);
        Long reservedId = saveReservation(schedule, seatIds.get(2), ReservationStatus.RESERVED);
        Reservation paying = reservationRepository.findById(payingId).orElseThrow();
        Reservation reserved = reservationRepository.findById(reservedId).orElseThrow();

        assertThatThrownBy(() -> reservationService.startPaying(pendingId))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> reservationService.confirmPaid(paying))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> reservationService.releaseUnpaid(paying))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> reservationService.releaseCanceled(reserved))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(reservationStatus(pendingId)).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservationStatus(payingId)).isEqualTo(ReservationStatus.PAYING);
        assertThat(reservationStatus(reservedId)).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(payingId)).hasSize(1);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservedId)).hasSize(1);
    }

    @Test
    void 기존_트랜잭션_안에서_호출하면_참여해_함께_커밋된다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item item = saveItem(5_000);
        saveStock(theater, item, 10);
        Order order = saveOrder(user, theater, item, 2);
        Schedule schedule = saveSchedule(1);
        Long payingId = saveReservation(schedule, seatIds().get(0), ReservationStatus.PAYING);

        transactionTemplate.executeWithoutResult(status -> {
            orderService.completePaidOrder(orderRepository.findById(order.getId()).orElseThrow());
            reservationService.confirmPaid(reservationRepository.findById(payingId).orElseThrow());
        });

        assertThat(stockQuantity(theater, item)).isEqualTo(8);
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.PAID);
        assertThat(reservationStatus(payingId)).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void 기존_트랜잭션이_롤백되면_참여한_재고_차감과_예매_상태_변경도_함께_롤백된다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item item = saveItem(5_000);
        saveStock(theater, item, 10);
        Order order = saveOrder(user, theater, item, 2);
        Schedule schedule = saveSchedule(2);
        List<Long> seatIds = seatIds();
        Long pendingId = saveReservation(schedule, seatIds.get(0), ReservationStatus.PENDING_PAYMENT);
        Long reservedId = saveReservation(schedule, seatIds.get(1), ReservationStatus.RESERVED);

        transactionTemplate.executeWithoutResult(status -> {
            reservationService.startPaying(pendingId);
            orderService.completePaidOrder(orderRepository.findById(order.getId()).orElseThrow());
            reservationService.releaseCanceled(reservationRepository.findById(reservedId).orElseThrow());
            status.setRollbackOnly();
        });

        assertThat(stockQuantity(theater, item)).isEqualTo(10);
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(reservationStatus(pendingId)).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservationStatus(reservedId)).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservedId)).hasSize(1);
    }

    @Test
    void 여러_상품_중_하나라도_재고가_부족하면_먼저_차감한_재고까지_롤백되고_보상_취소된다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item popcorn = saveItem(5_000);
        Item cola = itemRepository.save(Item.create("콜라", 2_500));
        saveStock(theater, popcorn, 10);
        saveStock(theater, cola, 1);
        Order order = orderRepository.save(Order.create(user, theater, 15_000));
        orderItemRepository.save(OrderItem.create(order, popcorn, 2, 5_000));
        orderItemRepository.save(OrderItem.create(order, cola, 2, 2_500));
        Payment payment = paymentRepository.save(Payment.forOrder(order));
        when(tossPaymentClient.confirm(any(), any(), anyInt(), any()))
                .thenReturn(tossResponse("DONE", payment, payment.getAmount(), 0));
        when(tossPaymentClient.cancel(any(), any(), any(), any()))
                .thenReturn(tossResponse("CANCELED", payment, 0, payment.getAmount()));

        assertThatThrownBy(() -> paymentFacade.confirm(user.getId(),
                new PaymentConfirmRequest("pk-" + payment.getId(), payment.getTossOrderId(), payment.getAmount())))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT409_1"));

        assertThat(stockQuantity(theater, popcorn)).isEqualTo(10);
        assertThat(stockQuantity(theater, cola)).isEqualTo(1);
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.CANCELED);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELED);
        verify(tossPaymentClient, times(1)).cancel(any(), any(), any(), any());
    }

    @Test
    void 승인_시작에서_결제를_선점하지_못하면_먼저_결제_중으로_바꾼_예매도_롤백된다() {
        Schedule schedule = saveSchedule(1);
        User user = saveUser();
        Long reservationId = reservationService.create(user.getId(),
                new ReservationCreateRequest(schedule.getId(), List.of(seatIds().get(0))));
        Payment payment = paymentRepository.findByReservationId(reservationId).orElseThrow();
        payment.expire();
        paymentRepository.save(payment);

        assertThatThrownBy(() -> paymentService.startApproval(user.getId(),
                new PaymentConfirmRequest("pk-" + payment.getId(), payment.getTossOrderId(), payment.getAmount())))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("PAYMENT409"));

        assertThat(reservationStatus(reservationId)).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    private Long saveReservation(Schedule schedule, Long seatId, ReservationStatus status) {
        Long reservationId = reservationService.create(saveUser().getId(),
                new ReservationCreateRequest(schedule.getId(), List.of(seatId)));
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        ReflectionTestUtils.setField(reservation, "status", status);
        reservationRepository.save(reservation);
        return reservationId;
    }

    private List<Long> seatIds() {
        return seatRepository.findAll().stream().map(Seat::getId).sorted().toList();
    }

    private int stockQuantity(Theater theater, Item item) {
        return stockRepository.findByTheaterIdAndItemId(theater.getId(), item.getId()).orElseThrow().getQuantity();
    }

    private OrderStatus orderStatus(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    private ReservationStatus reservationStatus(Long reservationId) {
        return reservationRepository.findById(reservationId).orElseThrow().getStatus();
    }
}
