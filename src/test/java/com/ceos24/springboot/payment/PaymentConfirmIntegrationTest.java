package com.ceos24.springboot.payment;

import com.ceos24.springboot.auth.jwt.JwtProvider;
import com.ceos24.springboot.movie.domain.AgeRating;
import com.ceos24.springboot.movie.domain.Movie;
import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.dto.PaymentConfirmRequest;
import com.ceos24.springboot.payment.dto.PaymentConfirmResponse;
import com.ceos24.springboot.payment.dto.PaymentPrepareResponse;
import com.ceos24.springboot.payment.dto.ReservationPaymentPrepareRequest;
import com.ceos24.springboot.payment.dto.TossPaymentConfirmRequest;
import com.ceos24.springboot.payment.dto.TossPaymentConfirmResponse;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.payment.service.PaymentService;
import com.ceos24.springboot.payment.service.PaymentRecoveryScheduler;
import com.ceos24.springboot.payment.service.PaymentConfirmTransactionService;
import com.ceos24.springboot.payment.toss.TossPaymentClient;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationSeat;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import com.ceos24.springboot.reservation.service.ReservationExpirationService;
import com.ceos24.springboot.reservation.service.ReservationExpirationScheduler;
import com.ceos24.springboot.reservation.service.ReservationService;
import com.ceos24.springboot.reservation.dto.ReservationCreateRequest;
import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.theater.domain.*;
import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:payment-confirm-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "payment.toss.secret-key=test_gsk_not_a_real_key"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentConfirmIntegrationTest {
    @Autowired private Clock clock;

    private static final String PAYMENT_KEY = "test_payment_key_for_duplicate_approval";
    private static final long AMOUNT = 28000L;
    private static final OffsetDateTime APPROVED_AT =
            OffsetDateTime.parse("2026-11-03T18:24:17+09:00");

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRecoveryScheduler recovery;
    @Autowired private PaymentConfirmTransactionService confirmationTransactions;
    @Autowired private PaymentRepository payments;
    @Autowired private ReservationRepository reservations;
    @Autowired private ReservationSeatRepository reservationSeats;
    @Autowired private ReservationExpirationService expiration;
    @Autowired private ReservationExpirationScheduler scheduler;
    @Autowired private ReservationService reservationService;
    @Autowired private com.ceos24.springboot.payment.service.PaymentConfirmService approval;

    // Only the external gateway is mocked; HTTP security, services and DB are real.
    @MockitoBean(enforceOverride = true)
    private TossPaymentClient tossPaymentClient;

    private TransactionTemplate transaction;
    private Long userId;
    private Long reservationId;
    private PaymentPrepareResponse prepared;
    private String token;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        // Do not wrap the test in @Transactional: begin/finish must really commit.
        transaction.executeWithoutResult(ignored -> {
            User user = persist(User.create("결제테스트", UUID.randomUUID() + "@payment.test",
                    "unused-for-jwt-test", "01012345678"));
            userId = user.getId();
            Theater theater = persist(Theater.create("결제테스트", Region.Seoul));
            Screen screen = persist(Screen.create(theater, "1관", ScreenType.General, 1, 2));
            Movie movie = persist(Movie.builder().titleKr("결제테스트")
                    .runtimeMinutes(120).ageRating(AgeRating.ALL).build());
            Screening screening = persist(Screening.builder().screen(screen).movie(movie)
                    .screeningDate(LocalDate.now(clock).plusDays(1))
                    .startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(16, 0))
                    .screeningType(ScreeningType.Original).build());
            LocalDateTime now = LocalDateTime.now(clock);
            Reservation reservation = persist(Reservation.builder().user(user).screening(screening)
                    .childCount(0).youthCount(0).adultCount(2).seniorCount(0)
                    .totalPrice((int) AMOUNT).status(ReservationStatus.Pending)
                    .reservationAt(now).expiresAt(now.plusMinutes(10)).build());
            reservationId = reservation.getReservationId();
            Seat firstSeat = persist(Seat.create(screen, "A", 1));
            Seat secondSeat = persist(Seat.create(screen, "A", 2));
            persist(ScreeningSeat.create(screening, firstSeat));
            persist(ScreeningSeat.create(screening, secondSeat));
            persist(ReservationSeat.create(reservation, screening, firstSeat));
            persist(ReservationSeat.create(reservation, screening, secondSeat));
        });

        prepared = paymentService.prepareReservation(userId,
                new ReservationPaymentPrepareRequest(reservationId));
        idempotencyKey = payments.findById(prepared.paymentId()).orElseThrow()
                .getConfirmIdempotencyKey();
        token = jwtProvider.createAccessToken(userId);
        when(tossPaymentClient.confirm(
                new TossPaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT),
                idempotencyKey
        )).thenReturn(new TossPaymentConfirmResponse(
                PAYMENT_KEY, prepared.orderId(), AMOUNT, "영화 티켓 예매",
                "DONE", "카드", APPROVED_AT, "KRW"
        ));
    }

    @AfterEach
    void cleanUp() {
        // This class has its own in-memory database, separate from local MySQL.
        transaction.executeWithoutResult(ignored -> {
            jdbc.update("delete from payment");
            jdbc.update("delete from reservation_seat");
            jdbc.update("delete from reservation");
            jdbc.update("delete from screening_seat");
            jdbc.update("delete from seat");
            jdbc.update("delete from screening");
            jdbc.update("delete from screen");
            jdbc.update("delete from theater");
            jdbc.update("delete from movie");
            jdbc.update("delete from cgv_user");
        });
    }

    @ParameterizedTest(name = "same completed request, hold deadline passed={0}")
    @ValueSource(booleans = {false, true})
    void repeatedApprovalReturnsStoredResultWithoutCallingTossAgain(boolean deadlinePassed) throws Exception {
        PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT);
        PaymentConfirmResponse first = approve(request);
        assertStoredCompletion();

        if (deadlinePassed) {
            LocalDateTime expiredAt = LocalDateTime.now(clock).minusMinutes(1);
            jdbc.update("update reservation set expires_at = ? where reservation_id = ?", expiredAt, reservationId);
            jdbc.update("update payment set expires_at = ? where payment_id = ?", expiredAt, prepared.paymentId());
        }

        PaymentConfirmResponse repeated = approve(request);

        assertThat(repeated).isEqualTo(first);
        assertThat(repeated.paymentId()).isEqualTo(prepared.paymentId());
        assertThat(repeated.reservationId()).isEqualTo(reservationId);
        assertStoredCompletion();
        verifySingleGatewayCall();
    }

    @ParameterizedTest(name = "completed request with changed {0} is rejected")
    @ValueSource(strings = {"amount", "paymentKey"})
    void changedDuplicateDoesNotReturnSuccessOrCallTossAgain(String changedField) throws Exception {
        approve(new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT));
        PaymentConfirmRequest changed = new PaymentConfirmRequest(
                changedField.equals("paymentKey") ? "different_payment_key" : PAYMENT_KEY,
                prepared.orderId(), changedField.equals("amount") ? AMOUNT + 1 : AMOUNT
        );

        mockMvc.perform(post("/api/payments/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(changed)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_INFORMATION_MISMATCH"));

        assertStoredCompletion();
        verifySingleGatewayCall();
    }

    private PaymentConfirmResponse approve(PaymentConfirmRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/payments/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.reservationStatus").value("Reserved"))
                .andExpect(jsonPath("$.amount").value(AMOUNT))
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readValue(body, PaymentConfirmResponse.class);
    }

    @Test
    void approvalCrossingDeadlineProtectsSeatsUntilCompletion() throws Exception {
        var request = new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("approval wait timed out");
            return approvedRemote();
        }).when(tossPaymentClient).confirm(any(), anyString());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> approval.confirm(userId, request));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                backdateExpiry();
                scheduler.expirePendingReservations();
                expiration.expireReservation(reservationId);
                assertThat(reservations.findById(reservationId).orElseThrow().isPaymentProcessing()).isTrue();
                assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).hasSize(2);
                assertError(() -> reservationService.cancelReservation(userId, reservationId), ErrorCode.PAYMENT_IN_PROGRESS);
                Long screeningId = jdbc.queryForObject("select screening_id from reservation where reservation_id = ?",
                        Long.class, reservationId);
                var seats = jdbc.queryForList("select seat_id from reservation_seat where reservation_id = ?", Long.class, reservationId);
                assertError(() -> reservationService.createReservation(userId,
                        new ReservationCreateRequest(screeningId, 0, 0, 2, 0, seats)), ErrorCode.SEAT_UNAVAILABLE);
            } finally {
                release.countDown();
            }
            assertThat(future.get(5, TimeUnit.SECONDS).reservationStatus()).isEqualTo(ReservationStatus.Reserved);
        }
        assertStoredCompletion();
        verifySingleGatewayCall();
    }

    @Test
    void duplicateWhileApprovalIsInFlightUsesLookupAndBothReturnSameCompletion() throws Exception {
        var request = new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("approval wait timed out");
            return approvedRemote();
        }).when(tossPaymentClient).confirm(any(), anyString());
        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenReturn(approvedRemote());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var original = executor.submit(() -> approval.confirm(userId, request));
            PaymentConfirmResponse duplicate;
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                duplicate = approval.confirm(userId, request);
            } finally {
                release.countDown();
            }
            assertThat(original.get(5, TimeUnit.SECONDS)).isEqualTo(duplicate);
        }
        assertStoredCompletion();
        verify(tossPaymentClient, times(1)).confirm(any(), eq(idempotencyKey));
        verify(tossPaymentClient, times(1)).findByPaymentKey(PAYMENT_KEY);
        verifyNoMoreInteractions(tossPaymentClient);
    }

    @Test
    void uncertainApprovalKeepsSeatsAfterDeadlineAndRecoversWithoutReposting() {
        var request = new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT);
        when(tossPaymentClient.confirm(any(), anyString())).thenThrow(new ResourceAccessException("simulated timeout"));
        assertError(() -> approval.confirm(userId, request), ErrorCode.PAYMENT_RESULT_UNKNOWN);
        verify(tossPaymentClient, never()).findByPaymentKey(anyString());
        recovery.recoverPayments();
        verify(tossPaymentClient, never()).findByPaymentKey(anyString());
        backdateExpiry();
        scheduler.expirePendingReservations();
        expiration.expireReservation(reservationId);
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(reservations.findById(reservationId).orElseThrow().isPaymentProcessing()).isTrue();
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).hasSize(2);
        assertError(() -> reservationService.cancelReservation(userId, reservationId), ErrorCode.PAYMENT_IN_PROGRESS);

        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenAnswer(ignored -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return approvedRemote();
        });
        makeRecoveryDue();
        recovery.recoverPayments();
        recovery.recoverPayments();
        assertStoredCompletion();
        verify(tossPaymentClient, times(1)).findByPaymentKey(PAYMENT_KEY);
        verify(tossPaymentClient, times(1)).confirm(
                new TossPaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT), idempotencyKey);
    }

    @Test
    void confirmedFailureAfterDeadlineExpiresReservationAndReleasesSeats() {
        var request = new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT);
        doAnswer(invocation -> {
            backdateExpiry();
            return new TossPaymentConfirmResponse(PAYMENT_KEY, prepared.orderId(), AMOUNT,
                    "영화 티켓 예매", "ABORTED", "카드", null, "KRW");
        }).when(tossPaymentClient).confirm(any(), anyString());
        assertError(() -> approval.confirm(userId, request), ErrorCode.PAYMENT_APPROVAL_FAILED);
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        Reservation saved = reservations.findById(reservationId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.Expired);
        assertThat(saved.isPaymentProcessing()).isFalse();
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).isEmpty();
    }

    private TossPaymentConfirmResponse approvedRemote() {
        return new TossPaymentConfirmResponse(PAYMENT_KEY, prepared.orderId(), AMOUNT,
                "영화 티켓 예매", "DONE", "카드", APPROVED_AT, "KRW");
    }

    @Test
    void differentPgCurrencyCannotCompleteThePayment() {
        when(tossPaymentClient.confirm(any(), anyString())).thenReturn(new TossPaymentConfirmResponse(
                PAYMENT_KEY, prepared.orderId(), AMOUNT, "영화 티켓 예매", "DONE", "카드", APPROVED_AT, "USD"));
        assertError(() -> approval.confirm(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT)),
                ErrorCode.PAYMENT_RESULT_UNKNOWN);
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        Reservation saved = reservations.findById(reservationId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.Pending);
        assertThat(saved.isPaymentProcessing()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "USER", "SCHEDULER", "SEATS"})
    void pendingPaymentExpiresTogetherWithReservation(String path) {
        backdateExpiry();

        switch (path) {
            case "DIRECT" -> expiration.expireReservation(reservationId);
            case "USER" -> expiration.releaseExpiredReservationsForUser(userId);
            case "SCHEDULER" -> scheduler.expirePendingReservations();
            case "SEATS" -> expiration.releaseExpiredSeats(
                    jdbc.queryForObject("select screening_id from reservation where reservation_id = ?",
                            Long.class, reservationId),
                    jdbc.queryForList("select seat_id from reservation_seat where reservation_id = ?",
                            Long.class, reservationId));
            default -> throw new IllegalArgumentException(path);
        }

        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.Expired);
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).isEmpty();
        expiration.expireReservation(reservationId);
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void pendingPaymentIsCanceledTogetherWithReservation() {
        reservationService.cancelReservation(userId, reservationId);

        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.Canceled);
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).isEmpty();
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void confirmedFailureBeforeDeadlineImmediatelyReleasesSeats() {
        when(tossPaymentClient.confirm(any(), anyString())).thenReturn(new TossPaymentConfirmResponse(
                PAYMENT_KEY, prepared.orderId(), AMOUNT, "영화 티켓 예매", "ABORTED", "카드", null, "KRW"));
        assertError(() -> approval.confirm(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT)),
                ErrorCode.PAYMENT_APPROVAL_FAILED);

        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.Canceled);
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).isEmpty();
        backdateExpiry();
        expiration.expireReservation(reservationId);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.Canceled);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422})
    void definiteCommandRejectionAndVerifiedNonApprovalFailAndReleaseSeats(int status) {
        when(tossPaymentClient.confirm(any(), anyString())).thenThrow(pgError(status));
        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenAnswer(ignored -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            // 조회 전에 거절 근거와 UNKNOWN이 이미 커밋되어 있어야 한다.
            var payment = payments.findById(prepared.paymentId()).orElseThrow();
            assertThat(payment.isRejectedByPg()).isTrue();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
            return unapprovedRemote("IN_PROGRESS");
        });
        assertError(() -> approval.confirm(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT)),
                ErrorCode.PAYMENT_APPROVAL_FAILED);
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.Canceled);
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).isEmpty();
        verify(tossPaymentClient, times(1)).findByPaymentKey(PAYMENT_KEY);
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 409, 429, 500, 503})
    void uncertainCommandErrorDoesNotTriggerImmediateLookupOrTreatInProgressAsFailure(int status) {
        when(tossPaymentClient.confirm(any(), anyString())).thenThrow(pgError(status));
        assertError(() -> approval.confirm(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT)),
                ErrorCode.PAYMENT_RESULT_UNKNOWN);
        verify(tossPaymentClient, never()).findByPaymentKey(anyString());
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().isRejectedByPg()).isFalse();
        makeRecoveryDue();
        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenReturn(unapprovedRemote("IN_PROGRESS"));
        recovery.recoverPayments();
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).hasSize(2);
    }

    @Test
    void approvedLookupWinsOverCommandRejection() {
        when(tossPaymentClient.confirm(any(), anyString())).thenThrow(pgError(400));
        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenReturn(approvedRemote());
        assertThat(approval.confirm(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT)).status())
                .isEqualTo(PaymentStatus.DONE);
        assertStoredCompletion();
    }

    @Test
    void rejectedApprovalWithFailedLookupIsRecoveredAfterDeadline() {
        when(tossPaymentClient.confirm(any(), anyString())).thenThrow(pgError(403));
        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenThrow(new ResourceAccessException("lookup timeout"));
        assertError(() -> approval.confirm(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT)),
                ErrorCode.PAYMENT_RESULT_UNKNOWN);
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().isRejectedByPg()).isTrue();
        backdateExpiry();
        expiration.expireReservation(reservationId);
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).hasSize(2);

        doReturn(unapprovedRemote("READY")).when(tossPaymentClient).findByPaymentKey(PAYMENT_KEY);
        makeRecoveryDue();
        recovery.recoverPayments();
        recovery.recoverPayments();
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.Expired);
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).isEmpty();
        verify(tossPaymentClient, times(1)).confirm(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_FOUND", "MISMATCH", "WAITING_FOR_DEPOSIT"})
    void rejectionWithoutVerifiedNonApprovalCannotReleaseSeats(String evidence) {
        when(tossPaymentClient.confirm(any(), anyString())).thenThrow(pgError(400));
        if (evidence.equals("NOT_FOUND")) {
            when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenThrow(pgError(404));
        } else if (evidence.equals("MISMATCH")) {
            when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenReturn(new TossPaymentConfirmResponse(
                    PAYMENT_KEY, "wrong-order", AMOUNT, "영화 티켓 예매", "READY", null, null, "KRW"));
        } else {
            when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenReturn(unapprovedRemote(evidence));
        }
        assertError(() -> approval.confirm(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT)),
                ErrorCode.PAYMENT_RESULT_UNKNOWN);
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).hasSize(2);
    }

    @Test
    void lookup4xxAfterTimeoutDoesNotBecomeCommandRejection() {
        when(tossPaymentClient.confirm(any(), anyString())).thenThrow(new ResourceAccessException("timeout"));
        var request = new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT);
        assertError(() -> approval.confirm(userId, request), ErrorCode.PAYMENT_RESULT_UNKNOWN);
        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenThrow(pgError(403));
        assertError(() -> approval.confirm(userId, request), ErrorCode.PAYMENT_RESULT_UNKNOWN);
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().isRejectedByPg()).isFalse();
        verify(tossPaymentClient, times(1)).findByPaymentKey(PAYMENT_KEY);
    }

    private RestClientResponseException pgError(int status) {
        return new RestClientResponseException("PG error", status, "error", null, null, null);
    }

    private TossPaymentConfirmResponse unapprovedRemote(String status) {
        return new TossPaymentConfirmResponse(PAYMENT_KEY, prepared.orderId(), AMOUNT,
                "영화 티켓 예매", status, null, null, "KRW");
    }

    @Test
    void crashedProcessingAndAbandonedRecoveryLeaseCanBeRecoveredFromCommittedData() {
        // begin이 커밋된 직후 프로세스가 종료되어 finish를 호출하지 못한 상황.
        confirmationTransactions.begin(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT));
        recovery.recoverPayments();
        verifyNoInteractions(tossPaymentClient);
        makeRecoveryDue();
        var abandoned = confirmationTransactions.claimRecovery(prepared.paymentId()).orElseThrow();
        assertThat(confirmationTransactions.claimRecovery(prepared.paymentId())).isEmpty();
        recovery.recoverPayments();
        verifyNoInteractions(tossPaymentClient);

        // 처리권 획득 후 서버가 종료돼도 DB의 임대가 만료되면 다른 실행이 이어받는다.
        makeRecoveryDue();
        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenReturn(approvedRemote());
        recovery.recoverPayments();
        // 종료 전 작업의 늦은 결과도 이미 완료된 결제를 되돌리지 않는다.
        confirmationTransactions.finish(abandoned, null);
        assertStoredCompletion();
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getRecovery().getAttempts()).isEqualTo(2);
        verify(tossPaymentClient, times(1)).findByPaymentKey(PAYMENT_KEY);
        verify(tossPaymentClient, never()).confirm(any(), anyString());
    }

    @Test
    void recoveryFailureAfterDeadlineReleasesSeatsOnlyAfterPgConfirmsFailure() {
        when(tossPaymentClient.confirm(any(), anyString())).thenThrow(new ResourceAccessException("timeout"));
        assertError(() -> approval.confirm(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT)),
                ErrorCode.PAYMENT_RESULT_UNKNOWN);
        backdateExpiry();
        makeRecoveryDue();
        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenThrow(new ResourceAccessException("still offline"));
        recovery.recoverPayments();
        scheduler.expirePendingReservations();
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).hasSize(2);
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getRecovery().getNextAttemptAt())
                .isAfter(LocalDateTime.now(clock));

        doReturn(new TossPaymentConfirmResponse(
                PAYMENT_KEY, prepared.orderId(), AMOUNT, "영화 티켓 예매", "ABORTED", null, null, "KRW"))
                .when(tossPaymentClient).findByPaymentKey(PAYMENT_KEY);
        makeRecoveryDue();
        recovery.recoverPayments();
        recovery.recoverPayments();
        assertThat(payments.findById(prepared.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.Expired);
        assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).isEmpty();
    }

    @Test
    void concurrentRecoveryAndExpirationKeepSeatsForLateApproval() throws Exception {
        confirmationTransactions.begin(userId, new PaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT));
        backdateExpiry();
        makeRecoveryDue();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(tossPaymentClient.findByPaymentKey(PAYMENT_KEY)).thenAnswer(ignored -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            started.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return approvedRemote();
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(recovery::recoverPayments);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                // 다른 서버의 스케줄러도 이미 임대한 작업을 중복 조회하지 않는다.
                recovery.recoverPayments();
                expiration.expireReservation(reservationId);
                scheduler.expirePendingReservations();
                assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).hasSize(2);
            } finally { release.countDown(); }
            future.get(5, TimeUnit.SECONDS);
        }
        assertStoredCompletion();
        verify(tossPaymentClient, times(1)).findByPaymentKey(PAYMENT_KEY);
    }

    private void makeRecoveryDue() {
        jdbc.update("update payment set next_recovery_at=? where payment_id=?",
                LocalDateTime.now(clock).minusMinutes(1), prepared.paymentId());
    }

    private void backdateExpiry() {
        jdbc.update("update reservation set expires_at = ? where reservation_id = ?", LocalDateTime.now(clock).minusMinutes(1), reservationId);
        jdbc.update("update payment set expires_at = ? where payment_id = ?", LocalDateTime.now(clock).minusMinutes(1), prepared.paymentId());
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GlobalException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private void assertStoredCompletion() {
        // Read committed data in a fresh transaction, not a cached fixture entity.
        transaction.executeWithoutResult(ignored -> {
            Payment payment = payments.findById(prepared.paymentId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
            assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
            assertThat(payment.getAmount()).isEqualTo(AMOUNT);
            assertThat(payment.getCurrency()).isEqualTo("KRW");
            assertThat(payment.getPaymentMethod()).isEqualTo("카드");
            assertThat(payment.getOrderName()).isEqualTo("영화 티켓 예매");
            assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT
                    .atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime());
            assertThat(payment.getConfirmIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(payments.count()).isEqualTo(1);
            Reservation reservation = reservations.findById(reservationId).orElseThrow();
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.Reserved);
            assertThat(reservation.isPaymentProcessing()).isFalse();
            assertThat(reservationSeats.findAllByReservation_ReservationId(reservationId)).hasSize(2);
        });
    }

    private void verifySingleGatewayCall() {
        verify(tossPaymentClient, times(1)).confirm(
                new TossPaymentConfirmRequest(PAYMENT_KEY, prepared.orderId(), AMOUNT), idempotencyKey);
        verifyNoMoreInteractions(tossPaymentClient);
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
