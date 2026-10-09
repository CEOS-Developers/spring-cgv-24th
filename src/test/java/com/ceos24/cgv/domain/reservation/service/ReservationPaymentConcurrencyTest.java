package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentOrderResponse;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest.SeatRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossConfirmResult;
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
import org.mockito.invocation.InvocationOnMock;
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

// 승인 T1이 커밋된 뒤에 토스를 부르는지, 그리고 그 사이를 다른 요청이 어떻게 보는지를 실제 MySQL에서 확인한다.
// 토스 호출을 붙잡아 두고 그 순간에 다른 요청을 보내야 해서 스레드를 직접 쓴다.
// 스레드마다 트랜잭션이 따로 열려야 해서 ControllerIntegrationTest(@Transactional)를 쓰지 않는다.
@SpringBootTest
@Import(MySqlContainerConfig.class)
class ReservationPaymentConcurrencyTest {

    @Autowired ReservationService reservationService;
    @Autowired ReservationPaymentService reservationPaymentService;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    @PersistenceContext EntityManager em;

    private Long screeningId;
    private Long ownerId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            Branch branch = TestFixtures.branch("결제동시성지점");
            em.persist(branch);
            Theater theater = TestFixtures.theater(branch, TheaterType.STANDARD, "1관");
            em.persist(theater);
            Movie movie = TestFixtures.movie("결제동시성영화");
            em.persist(movie);
            Screening screening = TestFixtures.screening(theater, movie, LocalDateTime.now().plusDays(1), 14000);
            em.persist(screening);
            User owner = TestFixtures.user("payowner");
            em.persist(owner);
            User other = TestFixtures.user("payother");
            em.persist(other);

            screeningId = screening.getId();
            ownerId = owner.getId();
            otherId = other.getId();
        });
    }

    // 커밋하는 테스트라 롤백에 기댈 수 없다. 결제가 예매를 참조하므로 먼저 지운다.
    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from Payment").executeUpdate();
            em.createQuery("delete from ReservationSeat").executeUpdate();
            em.createQuery("delete from Reservation").executeUpdate();
            em.createQuery("delete from Screening").executeUpdate();
            em.createQuery("delete from Theater").executeUpdate();
            em.createQuery("delete from Branch").executeUpdate();
            em.createQuery("delete from Movie").executeUpdate();
            em.createQuery("delete from User").executeUpdate();
        });
    }

    @Test
    void 토스_호출_중에는_예매_행을_잠그지_않고_같은_예매의_다른_결제와_취소를_막는다() throws Exception {
        Long id = hold(LocalDateTime.now());
        PaymentOrderResponse first = reservationService.createPaymentOrder(id, ownerId);
        PaymentOrderResponse second = reservationService.createPaymentOrder(id, ownerId);
        TossGate gate = new TossGate();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ReservationResponse> paying = pool.submit(() -> reservationPaymentService.confirm(id, ownerId, confirmOf(first)));
            gate.awaitEntered();

            // T1이 예매 행 락을 쥔 채 토스를 기다렸다면 아래 두 요청은 락 대기 한도(3초)에 걸려
            // 매핑된 409가 아니라 락 예외로 끝난다. 두 탭에서 각각 인증한 경우가 두 번째 결제다.
            assertThatThrownBy(() -> reservationPaymentService.confirm(id, ownerId, confirmOf(second)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
            assertThatThrownBy(() -> reservationService.startCancel(id, ownerId, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);

            gate.release();
            assertThat(paying.get(10, TimeUnit.SECONDS).status()).isEqualTo(ReservationStatus.RESERVED);
        } finally {
            gate.release();
            pool.shutdownNow();
        }
        verify(tossPaymentsClient, times(1)).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    void 토스_호출_중에_원래_선점_시각이_지나도_다른_회원이_좌석을_가져가지_못한다() throws Exception {
        // 선점이 3초 남았다. 승인 시작이 만료 시각을 늘리지 않으면 토스를 기다리는 사이 좌석이 풀린다
        Long id = hold(LocalDateTime.now().minusMinutes(10).plusSeconds(3));
        PaymentOrderResponse order = reservationService.createPaymentOrder(id, ownerId);
        TossGate gate = new TossGate();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ReservationResponse> paying = pool.submit(() -> reservationPaymentService.confirm(id, ownerId, confirmOf(order)));
            gate.awaitEntered();
            Thread.sleep(3500);

            assertThatThrownBy(() -> reservationService.create(otherId, seatRequest()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);

            gate.release();
            assertThat(paying.get(10, TimeUnit.SECONDS).status()).isEqualTo(ReservationStatus.RESERVED);
        } finally {
            gate.release();
            pool.shutdownNow();
        }
    }

    @Test
    void 같은_예매의_두_주문을_동시에_승인해도_토스는_한_번만_불린다() throws Exception {
        Long id = hold(LocalDateTime.now());
        List<PaymentOrderResponse> orders = List.of(
                reservationService.createPaymentOrder(id, ownerId),
                reservationService.createPaymentOrder(id, ownerId));
        // 토스 호출이 순식간에 끝나면 두 요청이 겹치지 않는다. 겹칠 틈을 둔다
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> {
            Thread.sleep(200);
            return approved(inv);
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try {
            for (PaymentOrderResponse order : orders) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return reservationPaymentService.confirm(id, ownerId, confirmOf(order));
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
            // 늦은 쪽은 앞선 승인이 진행 중이면 PAYMENT_IN_PROGRESS, 이미 끝났으면 RESERVATION_NOT_PENDING이다
            assertThat(outcomes).filteredOn(ErrorCode.class::isInstance)
                    .singleElement()
                    .isIn(ErrorCode.PAYMENT_IN_PROGRESS, ErrorCode.RESERVATION_NOT_PENDING);
        } finally {
            pool.shutdownNow();
        }
        verify(tossPaymentsClient, times(1)).confirm(anyString(), anyString(), anyInt());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    // 토스 호출 안에 들어온 순간을 알리고, 테스트가 풀어 줄 때까지 응답을 붙잡아 둔다.
    private class TossGate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        TossGate() {
            given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> {
                entered.countDown();
                released.await(15, TimeUnit.SECONDS);
                return approved(inv);
            });
        }

        void awaitEntered() throws InterruptedException {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        }

        void release() {
            released.countDown();
        }
    }

    private Long hold(LocalDateTime selectedAt) {
        return transactionTemplate.execute(status -> {
            Reservation reservation = TestFixtures.hold(
                    em.find(User.class, ownerId), em.find(Screening.class, screeningId), selectedAt);
            reservation.addSeat(1, 1, AudienceType.ADULT);
            em.persist(reservation);
            return reservation.getId();
        });
    }

    private ReservationCreateRequest seatRequest() {
        return new ReservationCreateRequest(screeningId, List.of(new SeatRequest(1, 1, AudienceType.ADULT)));
    }

    private PaymentConfirmRequest confirmOf(PaymentOrderResponse order) {
        // paymentKey는 유니크다. 시도마다 토스가 다른 값을 주는 것을 흉내 낸다
        return new PaymentConfirmRequest("tgen_" + order.orderId(), order.orderId(), order.amount());
    }

    private static TossConfirmResult approved(InvocationOnMock inv) {
        return new Approved(TestFixtures.approvedToss(inv.getArgument(0), inv.getArgument(1),
                inv.<Integer>getArgument(2), OffsetDateTime.now()));
    }
}
