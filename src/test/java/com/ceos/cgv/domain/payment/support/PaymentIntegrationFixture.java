package com.ceos.cgv.domain.payment.support;

import com.ceos.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentOrderCreateRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.gateway.GatewayCancellation;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.gateway.GatewayPaymentStatus;
import com.ceos.cgv.domain.payment.gateway.PaymentGateway;
import com.ceos.cgv.domain.payment.service.PaymentQueryService;
import com.ceos.cgv.domain.payment.service.PaymentWorkflowService;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldExpiryService;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldService;
import com.ceos.cgv.domain.reservation.service.payment.ReservationPaymentService;
import com.ceos.cgv.support.DatabaseIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Isolated fixtures and bounded synchronization shared by payment database experiments. */
public abstract class PaymentIntegrationFixture extends DatabaseIntegrationTest {
    protected static final Long USER_ID = 8911L;
    @Autowired protected PaymentWorkflowService service;
    @Autowired protected PaymentQueryService queries;
    @Autowired protected SeatHoldService holds;
    @Autowired protected SeatHoldExpiryService expiry;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PlatformTransactionManager transactionManager;
    @Autowired protected EntityManager entityManager;
    @MockitoBean protected PaymentGateway gateway;
    @MockitoSpyBean protected ReservationPaymentService reservationPayments;

    @BeforeEach
    void seedPaymentFixture() {
        jdbc.update("INSERT INTO users(user_id,name,email) VALUES (8911,'회원','race@example.com'),"
                + "(8918,'타인','race-other@example.com')");
        jdbc.update("INSERT INTO cinemas(cinema_id,name,address) VALUES (8912,'영화관','서울')");
        jdbc.update("INSERT INTO screens(screen_id,cinema_id,screen_type,row_count,seats_per_row)"
                + " VALUES (8913,8912,'GENERAL',1,3)");
        jdbc.update("INSERT INTO movies(movie_id,title,description,running_time,release_date,age_rating,visibility)"
                + " VALUES (8914,'영화','설명',120,'2026-09-15','ALL','PUBLIC')");
        jdbc.update("INSERT INTO screenings(screening_id,movie_id,screen_id,start_at)"
                + " VALUES (8915,8914,8913,'2026-09-26 12:30:00')");
        jdbc.update("INSERT INTO screening_seats(screening_seat_id,screening_id,seat_row,seat_number)"
                + " VALUES (8916,8915,'A',1),(8917,8915,'A',2),(8919,8915,'A',3)");
    }

    protected PaymentOrderResponse order(Integer... seatNumbers) {
        return orderFor(USER_ID, seatNumbers);
    }

    protected PaymentOrderResponse orderFor(Long userId, Integer... seatNumbers) {
        List<ReservedSeatRequest> seats = List.of(seatNumbers).stream()
                .map(number -> new ReservedSeatRequest("A", number)).toList();
        Long reservationId = holds.create(userId, UUID.randomUUID(),
                new SeatHoldCreateRequest(8915L, seats)).response().reservationId();
        return service.createOrder(userId, new PaymentOrderCreateRequest(reservationId));
    }

    protected GatewayPayment done(PaymentOrderResponse order) {
        return GatewayPayment.builder().paymentKey("pay-" + order.orderId())
                .orderId(order.orderId()).totalAmount(order.totalAmount())
                .balanceAmount(order.totalAmount()).status(GatewayPaymentStatus.DONE)
                .approvedAt(Instant.now()).method("카드").build();
    }

    protected PaymentConfirmRequest approvalRequest(GatewayPayment payment) {
        return new PaymentConfirmRequest(payment.paymentKey(), payment.orderId(), payment.totalAmount());
    }

    protected PaymentOperationResponse approve(PaymentOrderResponse order) {
        GatewayPayment payment = done(order);
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            assertOutsideTransaction();
            return payment;
        });
        return service.confirm(USER_ID, UUID.randomUUID(), approvalRequest(payment));
    }

    protected GatewayPayment canceled(GatewayPayment original, long amount) {
        long remaining = original.balanceAmount() - amount;
        List<GatewayCancellation> cancellations = new java.util.ArrayList<>(original.cancellations());
        cancellations.add(new GatewayCancellation(UUID.randomUUID().toString(), amount, Instant.now(), "DONE"));
        return new GatewayPayment(original.paymentKey(), original.orderId(), original.totalAmount(), remaining,
                remaining == 0 ? GatewayPaymentStatus.CANCELED : GatewayPaymentStatus.PARTIAL_CANCELED,
                original.method(), original.approvedAt(), null, cancellations);
    }

    protected void expireFixture(Long reservationId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Reservation reservation = entityManager.find(Reservation.class, reservationId);
            ReflectionTestUtils.setField(reservation, "expiresAt", Instant.now().minusSeconds(60));
        });
    }

    protected String reservationStatus(PaymentOrderResponse order) {
        return jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=?",
                String.class, order.reservationId());
    }

    protected int occupiedSeats(PaymentOrderResponse order) {
        return jdbc.queryForObject("SELECT count(*) FROM screening_seats WHERE current_reservation_id=?",
                Integer.class, order.reservationId());
    }

    protected int canceledSeats(PaymentOrderResponse order) {
        return jdbc.queryForObject("SELECT count(*) FROM reserved_seats WHERE reservation_id=? AND canceled_at IS NOT NULL",
                Integer.class, order.reservationId());
    }

    protected static void assertOutsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    protected static void await(java.util.concurrent.CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(15, TimeUnit.SECONDS)).as("bounded request synchronization").isTrue();
    }

    protected static <T> List<T> concurrent(Callable<T> first, Callable<T> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> left = executor.submit(() -> { start.await(15, TimeUnit.SECONDS); return first.call(); });
            Future<T> right = executor.submit(() -> { start.await(15, TimeUnit.SECONDS); return second.call(); });
            return List.of(left.get(30, TimeUnit.SECONDS), right.get(30, TimeUnit.SECONDS));
        }
    }
}
