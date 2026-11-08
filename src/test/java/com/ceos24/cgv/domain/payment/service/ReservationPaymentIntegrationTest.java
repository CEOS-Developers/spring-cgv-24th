package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.cinema.entity.*;
import com.ceos24.cgv.domain.cinema.repository.*;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.TossPaymentException;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.controller.PaymentController;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentCommandResponse;
import com.ceos24.cgv.domain.payment.entity.*;
import com.ceos24.cgv.domain.payment.repository.PaymentOperationRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.controller.ReservationController;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationCreateResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.reservation.service.ReservationHoldService;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.screening.service.ScreeningService;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.handler.GlobalExceptionHandler;
import jakarta.persistence.EntityManager;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.Advised;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationPaymentIntegrationTest {
    private static AnnotationConfigApplicationContext context;
    private static ReservationService reservations;
    private static ScreeningService screenings;
    private static PaymentService payments;
    private static PaymentTransactionService transactions;
    private static TossPaymentClient toss;
    private static EntityManager em;
    private static TransactionTemplate database;

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        reservations = context.getBean(ReservationService.class);
        screenings = context.getBean(ScreeningService.class);
        payments = context.getBean(PaymentService.class);
        transactions = context.getBean(PaymentTransactionService.class);
        toss = context.getBean(TossPaymentClient.class);
        em = context.getBean(EntityManager.class);
        database = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); }

    @BeforeEach
    void resetClient() { reset(toss); }

    @Test
    void 예매생성은_대기상태와_서버금액_결제를_저장하고_좌석조회와_다른예매를_차단한다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);

        assertEquals(ReservationStatus.PENDING_PAYMENT, created.status());
        assertEquals(30_000, created.totalAmount());
        assertNotNull(created.paymentId());
        assertTrue(created.orderId().startsWith("cgv_reservation_"));
        assertTrue(created.expiresAt().isAfter(created.reservedAt()));
        assertTrue(created.expiresAt().isBefore(created.reservedAt().plusMinutes(10).plusSeconds(1)));
        assertStates(created, ReservationStatus.PENDING_PAYMENT, PaymentStatus.READY);
        assertTrue(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
        assertError(ErrorCode.SEAT_ALREADY_RESERVED, () -> create(fixture));
        assertEquals(15_000, reservations.createReservation(fixture.username(),
                new ReservationCreateRequest(fixture.screeningId(), List.of(fixture.seatIds().get(2)))).totalAmount());
        verifyNoInteractions(toss);
    }

    @Test
    void 유효시간은_상영시작을_넘지않고_금액은_int범위를_넘어도_서버계산을_보존한다() {
        Fixture soon = fixture(15_000, Duration.ofMinutes(5));
        ReservationCreateResponse created = create(soon);
        assertEquals(soon.startsAt(), created.expiresAt());
        Fixture expensive = fixture(Integer.MAX_VALUE, Duration.ofDays(1));
        assertEquals(4_294_967_294L, create(expensive).totalAmount());
    }

    @Test
    void 시작된회차와_0원예매는_주문이나결제를_만들지않는다() {
        Fixture started = fixture(15_000, Duration.ofMinutes(-5));
        assertError(ErrorCode.SCREENING_ALREADY_STARTED, () -> create(started));
        Fixture free = fixture(0, Duration.ofDays(1));
        assertError(ErrorCode.INVALID_RESERVATION_AMOUNT, () -> create(free));
        assertReservationCount(started, 0);
        assertReservationCount(free, 0);
        verifyNoInteractions(toss);
    }

    @Test
    void 토스승인_성공후에만_예매가확정되고_재요청과_가격변경에도_원래금액을_유지한다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        when(toss.confirm(any(), anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertStates(created, ReservationStatus.PENDING_PAYMENT, PaymentStatus.READY);
            assertEquals(PaymentOperationStatus.PROCESSING, operationStatus(created));
            return approved(created);
        });
        database.executeWithoutResult(status -> em.createNativeQuery("update screening set price = 1 where id = :id")
                .setParameter("id", fixture.screeningId()).executeUpdate());

        PaymentCommandResponse receipt = payments.confirm(fixture.username(), confirm(created), "approve-movie");

        assertEquals(PaymentTargetType.RESERVATION, receipt.targetType());
        assertEquals(created.reservationId(), receipt.targetId());
        assertStates(created, ReservationStatus.CONFIRMED, PaymentStatus.DONE);
        var detail = reservations.getReservation(fixture.username(), created.reservationId());
        assertNotNull(detail.confirmedAt());
        assertEquals(30_000, detail.totalAmount());
        assertEquals(30_000, detail.totalPrice());
        assertEquals(created.orderId(), detail.orderId());
        assertEquals(created.paymentId(), detail.paymentId());
        assertEquals(receipt, payments.confirm(fixture.username(), confirm(created), "approve-movie"));
        assertEquals(detail.confirmedAt(), reservations.getReservation(fixture.username(), created.reservationId()).confirmedAt());
        assertTrue(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
        verify(toss, times(1)).confirm(any(), anyString());
    }

    @Test
    void 타인과_변조된금액은_PG호출전에_거절하고_좌석을_유지한다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        Fixture other = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        assertError(ErrorCode.PAYMENT_ACCESS_DENIED,
                () -> payments.confirm(other.username(), confirm(created), "foreign"));
        assertError(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> payments.confirm(fixture.username(),
                new PaymentConfirmRequest(paymentKey(created), created.orderId(), 1), "wrong-amount"));
        assertError(ErrorCode.RESERVATION_ACCESS_DENIED,
                () -> reservations.cancelReservation(other.username(), created.reservationId()));
        assertStates(created, ReservationStatus.PENDING_PAYMENT, PaymentStatus.READY);
        verifyNoInteractions(toss);
    }

    @Test
    void 결제저장에실패하면_예매와좌석점유도_함께롤백한다() throws Exception {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        Advised repository = (Advised) context.getBean(PaymentRepository.class);
        MethodInterceptor failure = invocation -> {
            if (invocation.getMethod().getName().equals("save")) throw new IllegalStateException("simulated payment insert failure");
            return invocation.proceed();
        };
        repository.addAdvice(0, failure);
        try {
            assertThrows(IllegalStateException.class, () -> create(fixture));
        } finally {
            repository.removeAdvice(failure);
        }
        assertReservationCount(fixture, 0);
        assertFalse(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
        database.executeWithoutResult(status -> assertEquals(0L, em.createQuery(
                "select count(rs) from ReservationSeat rs where rs.reservation.screening.id = :id", Long.class)
                .setParameter("id", fixture.screeningId()).getSingleResult()));
    }

    @Test
    @Timeout(20)
    void 같은좌석을_동시에_예매해도_결제대기예매는_한건만_생성된다() throws Exception {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        var pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) results.add(pool.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    create(fixture);
                    return true;
                } catch (BusinessException exception) {
                    assertEquals(ErrorCode.SEAT_ALREADY_RESERVED, exception.getErrorCode());
                    return false;
                }
            }));
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) succeeded++;
            assertEquals(1, succeeded);
            assertReservationCount(fixture, 1);
            database.executeWithoutResult(status -> assertEquals(1L, em.createQuery(
                    "select count(p) from Payment p where p.reservation.screening.id = :id", Long.class)
                    .setParameter("id", fixture.screeningId()).getSingleResult()));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(20)
    void 승인중_만료와취소와중복요청이_들어와도_좌석을유지하고_한번만_확정한다() throws Exception {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(toss.confirm(any(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return approved(created);
        });
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<PaymentCommandResponse> first = pool.submit(() -> payments.confirm(fixture.username(), confirm(created), "approve-movie"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            expire(created);
            assertTrue(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
            assertError(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS,
                    () -> reservations.cancelReservation(fixture.username(), created.reservationId()));
            assertError(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS,
                    () -> payments.confirm(fixture.username(), confirm(created), "approve-movie"));
            assertError(ErrorCode.SEAT_ALREADY_RESERVED, () -> create(fixture));
            release.countDown();
            assertEquals(PaymentStatus.DONE, first.get(5, TimeUnit.SECONDS).paymentStatus());
            assertStates(created, ReservationStatus.CONFIRMED, PaymentStatus.DONE);
            verify(toss, times(1)).confirm(any(), anyString());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 타임아웃은_예매를확정하거나좌석을해제하지않고_같은요청의_재조회로복구한다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        when(toss.confirm(any(), anyString())).thenThrow(TossPaymentException.communicationError(new RuntimeException("timeout")));
        assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN,
                () -> payments.confirm(fixture.username(), confirm(created), "approve-movie"));
        expire(created);
        assertStates(created, ReservationStatus.PENDING_PAYMENT, PaymentStatus.READY);
        assertEquals(PaymentOperationStatus.UNKNOWN, operationStatus(created));
        assertTrue(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
        assertError(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS,
                () -> reservations.cancelReservation(fixture.username(), created.reservationId()));
        assertError(ErrorCode.SEAT_ALREADY_RESERVED, () -> create(fixture));

        when(toss.getPayment(paymentKey(created))).thenReturn(approved(created));
        payments.confirm(fixture.username(), confirm(created), "approve-movie");

        assertStates(created, ReservationStatus.CONFIRMED, PaymentStatus.DONE);
        verify(toss, times(1)).confirm(any(), anyString());
        verify(toss, times(1)).getPayment(paymentKey(created));
    }

    @Test
    void PG성공후_커밋실패는_예매확정도롤백하고_복구할때_함께확정한다() throws Exception {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        when(toss.confirm(any(), anyString())).thenReturn(approved(created));
        AtomicBoolean failOnce = new AtomicBoolean(true);
        MethodInterceptor failure = invocation -> {
            if (invocation.getMethod().getName().equals("complete") && failOnce.getAndSet(false)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("simulated commit failure"); }
                });
            }
            return invocation.proceed();
        };
        Advised proxy = (Advised) transactions;
        proxy.addAdvice(failure);
        try {
            assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN,
                    () -> payments.confirm(fixture.username(), confirm(created), "approve-movie"));
            assertStates(created, ReservationStatus.PENDING_PAYMENT, PaymentStatus.READY);
            assertNull(reservations.getReservation(fixture.username(), created.reservationId()).confirmedAt());
            assertEquals(PaymentOperationStatus.UNKNOWN, operationStatus(created));
            assertTrue(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
            when(toss.getPayment(paymentKey(created))).thenReturn(approved(created));
            payments.confirm(fixture.username(), confirm(created), "approve-movie");
            assertStates(created, ReservationStatus.CONFIRMED, PaymentStatus.DONE);
        } finally {
            proxy.removeAdvice(failure);
        }
    }

    @Test
    void 명확한승인거절은_예매를취소하고_확보좌석을다시_예약할수있다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        when(toss.confirm(any(), anyString())).thenThrow(TossPaymentException.apiError(400, "REJECT_CARD_PAYMENT", "카드 거절"));
        assertError(ErrorCode.PAYMENT_OPERATION_FAILED,
                () -> payments.confirm(fixture.username(), confirm(created), "approve-movie"));
        assertStates(created, ReservationStatus.CANCELLED, PaymentStatus.ABORTED);
        assertFalse(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
        assertNotEquals(created.reservationId(), create(fixture).reservationId());
    }

    @Test
    void 미처리만료예매는_좌석조회때_정리되고_다시예약할수있다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        expire(created);
        assertFalse(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
        assertStates(created, ReservationStatus.EXPIRED, PaymentStatus.EXPIRED);
        assertNotEquals(created.reservationId(), create(fixture).reservationId());
        verifyNoInteractions(toss);
    }

    @Test
    void 같은회차의_여러만료예매를_한요청에서_모두정리한다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse first = create(fixture);
        ReservationCreateResponse second = reservations.createReservation(fixture.username(),
                new ReservationCreateRequest(fixture.screeningId(), List.of(fixture.seatIds().get(2))));
        expire(first);
        expire(second);
        assertTrue(screenings.getSeatsByScreening(fixture.screeningId()).stream().noneMatch(seat -> seat.reserved()));
        assertStates(first, ReservationStatus.EXPIRED, PaymentStatus.EXPIRED);
        assertStates(second, ReservationStatus.EXPIRED, PaymentStatus.EXPIRED);
        verifyNoInteractions(toss);
    }

    @Test
    void 만료뒤첫요청이_승인이어도_PG호출없이_만료하고좌석을해제한다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        expire(created);
        assertError(ErrorCode.PAYMENT_ORDER_EXPIRED,
                () -> payments.confirm(fixture.username(), confirm(created), "approve-movie"));
        assertStates(created, ReservationStatus.EXPIRED, PaymentStatus.EXPIRED);
        assertFalse(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
        verifyNoInteractions(toss);
    }

    @Test
    void 미결제예매취소는_결제도종료하고_이후승인을차단한다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        reservations.cancelReservation(fixture.username(), created.reservationId());
        assertStates(created, ReservationStatus.CANCELLED, PaymentStatus.ABORTED);
        assertFalse(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
        assertError(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE,
                () -> payments.confirm(fixture.username(), confirm(created), "approve-movie"));
        verifyNoInteractions(toss);
    }

    @Test
    void 결제후_직접예매취소와_금액부분취소를막고_PG전체취소성공때만_좌석을해제한다() {
        Fixture fixture = fixture(15_000, Duration.ofDays(1));
        ReservationCreateResponse created = create(fixture);
        when(toss.confirm(any(), anyString())).thenReturn(approved(created));
        PaymentCommandResponse approval = payments.confirm(fixture.username(), confirm(created), "approve-movie");
        assertError(ErrorCode.RESERVATION_PAYMENT_CANCEL_REQUIRED,
                () -> reservations.cancelReservation(fixture.username(), created.reservationId()));
        assertError(ErrorCode.RESERVATION_PARTIAL_CANCEL_NOT_SUPPORTED,
                () -> payments.cancel(fixture.username(), created.paymentId(), new PaymentCancelRequest("일부", 15_000L), "partial-movie"));
        verify(toss, never()).cancel(anyString(), any(), anyString());
        assertStates(created, ReservationStatus.CONFIRMED, PaymentStatus.DONE);

        when(toss.cancel(anyString(), any(), anyString())).thenReturn(canceled(created));
        PaymentCancelRequest cancel = new PaymentCancelRequest("전체 취소", null);
        PaymentCommandResponse receipt = payments.cancel(fixture.username(), created.paymentId(), cancel, "cancel-movie");
        assertEquals(PaymentStatus.CANCELED, receipt.paymentStatus());
        assertStates(created, ReservationStatus.CANCELLED, PaymentStatus.CANCELED);
        assertFalse(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
        assertEquals(receipt, payments.cancel(fixture.username(), created.paymentId(), cancel, "cancel-movie"));
        assertEquals(approval, payments.confirm(fixture.username(), confirm(created), "approve-movie"));
        assertStates(created, ReservationStatus.CANCELLED, PaymentStatus.CANCELED);
        verify(toss, times(1)).cancel(anyString(), any(), anyString());
    }

    @Nested
    class CommonPaymentApi {
        private MockMvc mvc;
        private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

        @BeforeEach
        void setUpApi() {
            // PG만 대체하고 컨트롤러 → 실제 서비스 프록시 → JPA 트랜잭션을 연결한다.
            mvc = MockMvcBuilders.standaloneSetup(new ReservationController(reservations), new PaymentController(payments))
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
        }

        @AfterEach
        void clearUser() { SecurityContextHolder.clearContext(); }

        @ParameterizedTest
        @CsvSource(delimiter = '|', textBlock = """
                /api/me/payments/confirm | false | {"paymentKey":"pg-key","orderId":"cgv_store_order","amount":10000}
                /api/me/payments/confirm | true  | {"paymentKey":"pg-key","orderId":"cgv_store_order","amount":0}
                /api/me/payments/1/cancellations | true | {"cancelReason":"취소","cancelAmount":-1}
                /api/me/reservations | false | {"screeningId":1,"seatIds":[]}
                """)
        void 잘못된_HTTP요청은_실제서비스처리전에_거절한다(String path, boolean withKey, String body) throws Exception {
            authenticate("validation-user");
            var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
            if (withKey) request.header("Idempotency-Key", "validation-key");

            mvc.perform(request).andExpect(status().isBadRequest());
            verifyNoInteractions(toss);
        }

        @Test
        void 예매생성후_기존승인과조회_API로_대기에서확정까지_연결한다() throws Exception {
            Fixture fixture = fixture(15_000, Duration.ofDays(1));
            authenticate(fixture.username());
            String response = mvc.perform(post("/api/me/reservations").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new ReservationCreateRequest(
                                    fixture.screeningId(), fixture.seatIds().subList(0, 2)))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.result.status").value("PENDING_PAYMENT"))
                    .andReturn().getResponse().getContentAsString();
            ReservationCreateResponse created = json.treeToValue(json.readTree(response).get("result"), ReservationCreateResponse.class);

            mvc.perform(get("/api/me/payments/{id}", created.paymentId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("READY"))
                    .andExpect(jsonPath("$.result.targetType").value("RESERVATION"))
                    .andExpect(jsonPath("$.result.reservationId").value(created.reservationId()))
                    .andExpect(jsonPath("$.result.reservationStatus").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.result.confirmedAt").isEmpty())
                    .andExpect(jsonPath("$.result.purchaseId").isEmpty())
                    .andExpect(jsonPath("$.result.operations").isEmpty());
            verifyNoInteractions(toss);

            when(toss.confirm(any(), anyString())).thenReturn(approved(created));
            String receipt = mvc.perform(approval(created, "http-approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.paymentStatus").value("DONE"))
                    .andExpect(jsonPath("$.result.targetType").value("RESERVATION"))
                    .andExpect(jsonPath("$.result.targetId").value(created.reservationId()))
                    .andExpect(jsonPath("$.result.reservationId").value(created.reservationId()))
                    .andReturn().getResponse().getContentAsString();
            mvc.perform(approval(created, "http-approve"))
                    .andExpect(status().isOk()).andExpect(content().json(receipt));
            mvc.perform(get("/api/me/payments/{id}", created.paymentId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("DONE"))
                    .andExpect(jsonPath("$.result.reservationStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.result.confirmedAt").isNotEmpty())
                    .andExpect(jsonPath("$.result.operations.length()").value(1))
                    .andExpect(jsonPath("$.result.operations[0].status").value("SUCCEEDED"));
            mvc.perform(get("/api/me/reservations/{id}", created.reservationId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.result.paymentId").value(created.paymentId()));
            assertStates(created, ReservationStatus.CONFIRMED, PaymentStatus.DONE);
            verify(toss, times(1)).confirm(any(), anyString());
            verify(toss, never()).getPayment(anyString());
        }

        @Test
        void 결과불명은_조회로확정하지않고_같은승인요청으로만_복구한다() throws Exception {
            Fixture fixture = fixture(15_000, Duration.ofDays(1));
            ReservationCreateResponse created = create(fixture);
            authenticate(fixture.username());
            when(toss.confirm(any(), anyString())).thenThrow(
                    TossPaymentException.communicationError(new RuntimeException("timeout")));

            mvc.perform(approval(created, "http-recover"))
                    .andExpect(status().isBadGateway()).andExpect(jsonPath("$.divisionCode").value("PAY009"));
            clearInvocations(toss);
            mvc.perform(get("/api/me/payments/{id}", created.paymentId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("READY"))
                    .andExpect(jsonPath("$.result.reservationStatus").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.result.confirmedAt").isEmpty())
                    .andExpect(jsonPath("$.result.operations[0].status").value("UNKNOWN"));
            assertTrue(screenings.getSeatsByScreening(fixture.screeningId()).getFirst().reserved());
            verifyNoInteractions(toss);

            when(toss.getPayment(paymentKey(created))).thenReturn(approved(created));
            mvc.perform(approval(created, "http-recover")).andExpect(status().isOk());
            mvc.perform(get("/api/me/payments/{id}", created.paymentId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("DONE"))
                    .andExpect(jsonPath("$.result.reservationStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.result.operations[0].status").value("SUCCEEDED"));
            verify(toss, times(1)).getPayment(paymentKey(created));
            verify(toss, never()).confirm(any(), anyString());
        }

        @Test
        void 취소후_승인재응답은_원래결과이고_조회는_현재예매상태를_반환한다() throws Exception {
            Fixture fixture = fixture(15_000, Duration.ofDays(1));
            ReservationCreateResponse created = create(fixture);
            authenticate(fixture.username());
            when(toss.confirm(any(), anyString())).thenReturn(approved(created));
            String receipt = mvc.perform(approval(created, "http-approve"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            mvc.perform(delete("/api/me/reservations/{id}", created.reservationId()))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.divisionCode").value("R009"));
            when(toss.cancel(anyString(), any(), anyString())).thenReturn(canceled(created));
            mvc.perform(post("/api/me/payments/{id}/cancellations", created.paymentId())
                            .header("Idempotency-Key", "http-cancel").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new PaymentCancelRequest("예매 전체 취소", null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.reservationId").value(created.reservationId()));

            mvc.perform(approval(created, "http-approve"))
                    .andExpect(status().isOk()).andExpect(content().json(receipt));
            mvc.perform(get("/api/me/payments/{id}", created.paymentId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("CANCELED"))
                    .andExpect(jsonPath("$.result.reservationStatus").value("CANCELLED"))
                    .andExpect(jsonPath("$.result.confirmedAt").isNotEmpty())
                    .andExpect(jsonPath("$.result.operations.length()").value(2));
            assertStates(created, ReservationStatus.CANCELLED, PaymentStatus.CANCELED);
            verify(toss, times(1)).confirm(any(), anyString());
        }

        private MockHttpServletRequestBuilder approval(ReservationCreateResponse created, String key) {
            return post("/api/me/payments/confirm").header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(confirm(created)));
        }

        private void authenticate(String username) {
            var user = User.withUsername(username).password("encoded").roles("USER").build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        }
    }

    private ReservationCreateResponse create(Fixture fixture) {
        return reservations.createReservation(fixture.username(),
                new ReservationCreateRequest(fixture.screeningId(), fixture.seatIds().subList(0, 2)));
    }

    private Fixture fixture(int price, Duration startsIn) {
        return database.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            UserEntity user = UserEntity.createLocalUser("movie-" + suffix, "encoded", "예매자");
            Cinema cinema = Cinema.create("예매 영화관", "주소", "서울");
            em.persist(user);
            em.persist(cinema);
            AuditoriumType type = AuditoriumType.create("type-" + suffix, AuditoriumTypeCategory.NORMAL, 1, 3);
            em.persist(type);
            Auditorium auditorium = Auditorium.create(cinema, type, "auditorium-" + suffix);
            em.persist(auditorium);
            Movie movie = Movie.create("테스트 영화", LocalDate.now());
            em.persist(movie);
            LocalDateTime startsAt = LocalDateTime.now().plus(startsIn).withNano(0);
            Screening screening = Screening.create(auditorium, movie, startsAt, startsAt.plusHours(2), price);
            em.persist(screening);
            List<Long> seatIds = new ArrayList<>();
            for (int column = 1; column <= 3; column++) {
                Seat seat = Seat.create(auditorium, 1, column);
                em.persist(seat);
                seatIds.add(seat.getId());
            }
            em.flush();
            return new Fixture(user.getUsername(), screening.getId(), startsAt, seatIds);
        });
    }

    private PaymentConfirmRequest confirm(ReservationCreateResponse created) {
        return new PaymentConfirmRequest(paymentKey(created), created.orderId(), created.totalAmount());
    }

    private String paymentKey(ReservationCreateResponse created) { return "movie-pg-" + created.paymentId(); }

    private TossPaymentResponse approved(ReservationCreateResponse created) {
        return new TossPaymentResponse(paymentKey(created), created.orderId(), "DONE", "카드", "KRW",
                created.totalAmount(), created.totalAmount(), OffsetDateTime.now(), "approval-" + created.paymentId(), List.of());
    }

    private TossPaymentResponse canceled(ReservationCreateResponse created) {
        String key = "cancel-" + created.paymentId();
        return new TossPaymentResponse(paymentKey(created), created.orderId(), "CANCELED", "카드", "KRW",
                created.totalAmount(), 0L, OffsetDateTime.now(), key,
                List.of(new TossPaymentResponse.Cancel(key, created.totalAmount(), OffsetDateTime.now(), 0L, "DONE")));
    }

    private void expire(ReservationCreateResponse created) {
        database.executeWithoutResult(status -> em.createNativeQuery("update payment set expires_at = :at where id = :id")
                .setParameter("at", LocalDateTime.now().minusSeconds(1)).setParameter("id", created.paymentId()).executeUpdate());
    }

    private void assertStates(ReservationCreateResponse created, ReservationStatus reservationStatus, PaymentStatus paymentStatus) {
        database.executeWithoutResult(status -> {
            assertEquals(reservationStatus, em.find(Reservation.class, created.reservationId()).getStatus());
            assertEquals(paymentStatus, em.find(Payment.class, created.paymentId()).getStatus());
        });
    }

    private PaymentOperationStatus operationStatus(ReservationCreateResponse created) {
        return database.execute(status -> em.createQuery("select o from PaymentOperation o where o.payment.id = :id", PaymentOperation.class)
                .setParameter("id", created.paymentId()).getSingleResult().getStatus());
    }

    private void assertReservationCount(Fixture fixture, long count) {
        database.executeWithoutResult(status -> assertEquals(count, em.createQuery(
                "select count(r) from Reservation r where r.screening.id = :id", Long.class)
                .setParameter("id", fixture.screeningId()).getSingleResult()));
    }

    private void assertError(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action).getErrorCode());
    }

    private record Fixture(String username, Long screeningId, LocalDateTime startsAt, List<Long> seatIds) { }

    @Configuration(proxyBeanMethods = false)
    @Import(PaymentServiceIntegrationTest.Config.class)
    static class Config {
        @Bean ReservationSeatRepository reservationSeats(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(ReservationSeatRepository.class); }
        @Bean ScreeningRepository screenings(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(ScreeningRepository.class); }
        @Bean SeatRepository seats(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(SeatRepository.class); }
        @Bean AuditoriumRepository auditoriums(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(AuditoriumRepository.class); }
        @Bean MovieRepository movies(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(MovieRepository.class); }
        @Bean CinemaRepository cinemas(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(CinemaRepository.class); }
        @Bean ReservationHoldService holds(PaymentRepository payments, PaymentTransactionService transactions) {
            return new ReservationHoldService(payments, transactions);
        }
        @Bean ReservationService reservationService(ReservationRepository reservations, ReservationSeatRepository seats,
                ScreeningRepository screenings, UserRepository users, SeatRepository seatRepository,
                PaymentRepository payments, PaymentOperationRepository operations, ReservationHoldService holds) {
            return new ReservationService(reservations, seats, screenings, users, seatRepository, payments, operations, holds);
        }
        @Bean ScreeningService screeningService(ScreeningRepository screenings, AuditoriumRepository auditoriums,
                MovieRepository movies, CinemaRepository cinemas, SeatRepository seats,
                ReservationSeatRepository reservationSeats, ReservationHoldService holds) {
            return new ScreeningService(screenings, auditoriums, movies, cinemas, seats, reservationSeats, holds);
        }
    }
}
