package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentOrderResponse;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelRequest.SeatPosition;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest.SeatRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.MySqlContainerConfig;
import com.ceos24.cgv.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 취소 T1이 커밋된 뒤에 토스를 부르는지, 그리고 토스가 환불하는 동안 다른 요청이 무엇을 보는지를 실제 MySQL에서 확인한다.
// 구조는 ReservationPaymentConcurrencyTest와 같다. 토스 취소 호출을 붙잡아 두고 그 순간 다른 요청을 보낸다.
@SpringBootTest
@Import(MySqlContainerConfig.class)
class ReservationCancelConcurrencyTest {

    private static final int SEAT_PRICE = 14000;

    @Autowired ReservationService reservationService;
    @Autowired ReservationPaymentService reservationPaymentService;
    @Autowired ReservationCancelService reservationCancelService;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    @PersistenceContext EntityManager em;

    private Long screeningId;
    private Long ownerId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            Branch branch = TestFixtures.branch("취소동시성지점");
            em.persist(branch);
            Theater theater = TestFixtures.theater(branch, TheaterType.STANDARD, "1관");
            em.persist(theater);
            Movie movie = TestFixtures.movie("취소동시성영화");
            em.persist(movie);
            Screening screening = TestFixtures.screening(theater, movie, LocalDateTime.now().plusDays(1), SEAT_PRICE);
            em.persist(screening);
            User owner = TestFixtures.user("cancelowner");
            em.persist(owner);
            User other = TestFixtures.user("cancelother");
            em.persist(other);

            screeningId = screening.getId();
            ownerId = owner.getId();
            otherId = other.getId();
        });
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> new Approved(
                TestFixtures.approvedToss(inv.getArgument(0), inv.getArgument(1), inv.<Integer>getArgument(2),
                        OffsetDateTime.now())));
    }

    // 커밋하는 테스트라 롤백에 기댈 수 없다. 좌석이 취소 요청을, 취소 요청이 결제를, 결제가 예매를 참조한다.
    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from ReservationSeat").executeUpdate();
            em.createQuery("delete from PaymentCancel").executeUpdate();
            em.createQuery("delete from Payment").executeUpdate();
            em.createQuery("delete from Reservation").executeUpdate();
            em.createQuery("delete from Screening").executeUpdate();
            em.createQuery("delete from Theater").executeUpdate();
            em.createQuery("delete from Branch").executeUpdate();
            em.createQuery("delete from Movie").executeUpdate();
            em.createQuery("delete from User").executeUpdate();
        });
    }

    @Test
    void 토스_취소_중에는_예매_행을_잠그지_않고_같은_결제의_다른_취소를_막는다() throws Exception {
        Long id = paidReservation(1, 2);
        TossCancelGate gate = new TossCancelGate(2 * SEAT_PRICE);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ReservationResponse> cancelling = pool.submit(() -> reservationCancelService.cancelSeats(id, ownerId, seats(1)));
            gate.awaitEntered();

            // T1이 예매 행 락을 쥔 채 토스를 기다렸다면 아래 두 요청은 락 대기 한도(3초)에 걸려
            // 매핑된 409가 아니라 락 예외로 끝난다
            assertThatThrownBy(() -> reservationCancelService.cancelSeats(id, ownerId, seats(2)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
            assertThatThrownBy(() -> reservationCancelService.cancel(id, ownerId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);

            gate.release();
            ReservationResponse cancelled = cancelling.get(10, TimeUnit.SECONDS);
            assertThat(cancelled.status()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(cancelled.remainingPrice()).isEqualTo(SEAT_PRICE);
        } finally {
            gate.release();
            pool.shutdownNow();
        }
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 토스가_환불하는_동안에는_취소할_좌석을_다른_회원이_가져가지_못한다() throws Exception {
        Long id = paidReservation(1, 2);
        TossCancelGate gate = new TossCancelGate(2 * SEAT_PRICE);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ReservationResponse> cancelling = pool.submit(() -> reservationCancelService.cancelSeats(id, ownerId, seats(1)));
            gate.awaitEntered();

            // 이때 좌석을 놓아 두었다면 환불이 거절됐을 때 되돌릴 수 없다
            assertThatThrownBy(() -> reservationService.create(otherId, seatRequest(1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);

            gate.release();
            cancelling.get(10, TimeUnit.SECONDS);
        } finally {
            gate.release();
            pool.shutdownNow();
        }
        assertThat(reservationService.create(otherId, seatRequest(1)).status()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void 같은_결제에_부분_취소_두_건이_동시에_와도_토스는_한_번만_불린다() throws Exception {
        Long id = paidReservation(1, 2);
        // 토스 호출이 순식간에 끝나면 두 요청이 겹치지 않는다. 겹칠 틈을 둔다
        var refunds = TestFixtures.tossCancelsFrom(2 * SEAT_PRICE);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString())).willAnswer(inv -> {
            Thread.sleep(200);
            return refunds.answer(inv);
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try {
            for (int col : new int[]{1, 2}) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return reservationCancelService.cancelSeats(id, ownerId, seats(col));
                    } catch (CustomException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            ready.await();
            go.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(15, TimeUnit.SECONDS));
            }
            assertThat(outcomes).filteredOn(ReservationResponse.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(ErrorCode.class::isInstance)
                    .containsExactly(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
        } finally {
            pool.shutdownNow();
        }
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    // 토스 취소 호출 안에 들어온 순간을 알리고, 테스트가 풀어 줄 때까지 응답을 붙잡아 둔다.
    private class TossCancelGate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        TossCancelGate(int paidAmount) {
            var refunds = TestFixtures.tossCancelsFrom(paidAmount);
            given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString())).willAnswer(inv -> {
                entered.countDown();
                released.await(15, TimeUnit.SECONDS);
                return refunds.answer(inv);
            });
        }

        void awaitEntered() throws InterruptedException {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        }

        void release() {
            released.countDown();
        }
    }

    // A열의 주어진 좌석들을 선점하고 결제까지 마친다.
    private Long paidReservation(int... cols) {
        Long id = transactionTemplate.execute(status -> {
            Reservation reservation = TestFixtures.hold(
                    em.find(User.class, ownerId), em.find(Screening.class, screeningId), LocalDateTime.now());
            for (int col : cols) {
                reservation.addSeat(1, col, AudienceType.ADULT);
            }
            em.persist(reservation);
            return reservation.getId();
        });
        PaymentOrderResponse order = reservationService.createPaymentOrder(id, ownerId);
        reservationPaymentService.confirm(id, ownerId,
                new PaymentConfirmRequest("tgen_" + order.orderId(), order.orderId(), order.amount()));
        return id;
    }

    private ReservationCancelRequest seats(int... cols) {
        List<SeatPosition> positions = new ArrayList<>();
        for (int col : cols) {
            positions.add(new SeatPosition(1, col));
        }
        return new ReservationCancelRequest(positions);
    }

    private ReservationCreateRequest seatRequest(int col) {
        return new ReservationCreateRequest(screeningId, List.of(new SeatRequest(1, col, AudienceType.ADULT)));
    }
}
