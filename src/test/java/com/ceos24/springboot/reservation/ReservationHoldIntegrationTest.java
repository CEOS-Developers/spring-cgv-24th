package com.ceos24.springboot.reservation;

import com.ceos24.springboot.auth.jwt.JwtProvider;
import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.movie.domain.AgeRating;
import com.ceos24.springboot.movie.domain.Movie;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.dto.*;
import com.ceos24.springboot.payment.service.PaymentConfirmService;
import com.ceos24.springboot.payment.service.PaymentConfirmTransactionService;
import com.ceos24.springboot.payment.service.PaymentService;
import com.ceos24.springboot.payment.toss.TossPaymentClient;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.dto.ReservationCreateRequest;
import com.ceos24.springboot.reservation.dto.ReservationResponse;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import com.ceos24.springboot.reservation.service.ReservationExpirationScheduler;
import com.ceos24.springboot.reservation.service.ReservationExpirationService;
import com.ceos24.springboot.reservation.service.ReservationService;
import com.ceos24.springboot.theater.domain.*;
import com.ceos24.springboot.theater.repository.ScreeningSeatRepository;
import com.ceos24.springboot.theater.service.ScreeningService;
import com.ceos24.springboot.theater.dto.ScreeningCreateRequest;
import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationHoldIntegrationTest {
    @Autowired private ReservationService service;
    @Autowired private ReservationExpirationService expirationService;
    @Autowired private ReservationExpirationScheduler scheduler;
    @Autowired private ReservationRepository reservations;
    @Autowired private ReservationSeatRepository reservationSeats;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentConfirmService paymentConfirmService;
    @Autowired private PaymentConfirmTransactionService paymentTransactions;
    @MockitoBean(enforceOverride = true) private TossPaymentClient tossPaymentClient;
    @Autowired private ScreeningSeatRepository screeningSeats;
    @Autowired private ScreeningService screeningService;

    private TransactionTemplate transaction;
    private Long userId;
    private Long otherUserId;
    private Long screeningId;
    private Long otherScreeningId;
    private Long firstSeatId;
    private Long secondSeatId;
    private Long screenId;
    private Long theaterId;
    private Long movieId;
    private Long priceId;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .isEqualTo(System.getenv().getOrDefault("SEAT_LOCK_TEST_DB", "H2"));
        }
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(ignored -> {
            String suffix = UUID.randomUUID().toString();
            userId = persist(User.create("선택자", suffix + "@hold.test", "unused", "010-1111-1111")).getId();
            otherUserId = persist(User.create("다른선택자", suffix + "@other.test", "unused", "010-2222-2222")).getId();
            Theater theater = persist(Theater.create("점유테스트", Region.Seoul));
            theaterId = theater.getTheaterId();
            Screen screen = persist(Screen.create(theater, "1관", ScreenType.General, 1, 2));
            screenId = screen.getScreenId();
            Movie movie = persist(Movie.builder().titleKr("점유테스트")
                    .runtimeMinutes(120).ageRating(AgeRating.ALL).build());
            movieId = movie.getMovieId();
            LocalDate date = LocalDate.now().plusDays(7);
            screeningId = persist(Screening.builder().screen(screen).movie(movie)
                    .screeningDate(date).startTime(LocalTime.NOON)
                    .endTime(LocalTime.of(14, 0)).screeningType(ScreeningType.Original)
                    .build()).getScreeningId();
            otherScreeningId = persist(Screening.builder().screen(screen).movie(movie)
                    .screeningDate(date).startTime(LocalTime.of(15, 0))
                    .endTime(LocalTime.of(17, 0)).screeningType(ScreeningType.Original)
                    .build()).getScreeningId();
            firstSeatId = persist(Seat.create(screen, "A", 1)).getSeatId();
            secondSeatId = persist(Seat.create(screen, "A", 2)).getSeatId();
            for (Long id : List.of(screeningId, otherScreeningId)) {
                for (Long seatId : List.of(firstSeatId, secondSeatId)) {
                    persist(ScreeningSeat.create(entityManager.find(Screening.class, id),
                            entityManager.find(Seat.class, seatId)));
                }
            }
            jdbc.update("""
                    insert into price (audience_type, day_type, time_type, screen_category, ticket_price)
                    values ('Adult', ?, 'Normal', 'General', 12345)
                    """, date.getDayOfWeek().getValue() >= 6 ? "Weekend" : "Weekday");
            priceId = jdbc.queryForObject("select price_id from price where ticket_price = 12345", Long.class);
        });
    }

    @AfterEach
    void cleanUp() {
        transaction.executeWithoutResult(ignored -> {
            jdbc.update("delete from payment where user_id in (?, ?)", userId, otherUserId);
            jdbc.update("delete from reservation_seat where screening_id in (?, ?)", screeningId, otherScreeningId);
            jdbc.update("delete from reservation where screening_id in (?, ?)", screeningId, otherScreeningId);
            jdbc.update("delete from screening_seat where screening_id in (?, ?)", screeningId, otherScreeningId);
            jdbc.update("delete from screening where screening_id in (?, ?)", screeningId, otherScreeningId);
            jdbc.update("delete from seat where screen_id = ?", screenId);
            jdbc.update("delete from screen where screen_id = ?", screenId);
            jdbc.update("delete from theater where theater_id = ?", theaterId);
            jdbc.update("delete from movie where movie_id = ?", movieId);
            jdbc.update("delete from cgv_user where user_id in (?, ?)", userId, otherUserId);
            jdbc.update("delete from price where price_id = ?", priceId);
        });
    }

    @Test
    void selectionCreatesPendingReservationForExactlyTenMinutes() {
        ReservationResponse response = create(userId, screeningId, firstSeatId);
        assertThat(response.status()).isEqualTo(ReservationStatus.Pending);
        assertThat(Duration.between(response.reservationAt(), response.expiresAt())).isEqualTo(Duration.ofMinutes(10));
        assertThat(reservationSeats.findAllByReservation_ReservationId(response.reservationId())).hasSize(1);
    }

    @Test
    void occupiedSeatConflictsButDifferentSeatAndDifferentScreeningAreAllowed() {
        create(userId, screeningId, firstSeatId);
        assertConflict(() -> create(otherUserId, screeningId, firstSeatId), ErrorCode.SEAT_UNAVAILABLE);
        assertThat(create(otherUserId, screeningId, secondSeatId).status()).isEqualTo(ReservationStatus.Pending);
        assertThat(create(otherUserId, otherScreeningId, firstSeatId).status()).isEqualTo(ReservationStatus.Pending);
    }

    @Test
    void failedMultiSeatSelectionDoesNotOccupyTheOtherSeat() {
        create(userId, screeningId, secondSeatId);
        assertConflict(() -> create(otherUserId, screeningId, firstSeatId, secondSeatId), ErrorCode.SEAT_UNAVAILABLE);
        assertThat(create(otherUserId, screeningId, firstSeatId).status()).isEqualTo(ReservationStatus.Pending);
    }

    @Test
    void paymentApprovalIsOwnedAndIdempotentAndKeepsSeatsOccupied() throws Exception {
        ReservationResponse pending = create(userId, screeningId, firstSeatId);
        PaymentConfirmRequest request = prepareApproval(userId, pending.reservationId());
        mockMvc.perform(post("/api/payments/confirm")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(otherUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        mockMvc.perform(post("/api/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tossPaymentClient);
        mockMvc.perform(post("/api/payments/confirm")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.reservationStatus").value("Reserved"));
        assertThat(paymentConfirmService.confirm(userId, request).reservationStatus())
                .isEqualTo(ReservationStatus.Reserved);
        verify(tossPaymentClient, times(1)).confirm(any(), anyString());
        verifyNoMoreInteractions(tossPaymentClient);
        backdateExpiry(pending.reservationId());
        expirationService.expireReservation(pending.reservationId());
        assertThat(savedStatus(pending.reservationId())).isEqualTo(ReservationStatus.Reserved);
        assertConflict(() -> create(otherUserId, screeningId, firstSeatId), ErrorCode.SEAT_UNAVAILABLE);
    }

    @Test
    void cancellationReturnsCanceledAndReleasesSeatsWithoutCancellationHistory() throws Exception {
        ReservationResponse pending = create(userId, screeningId, firstSeatId);
        mockMvc.perform(patch("/api/reservations/{id}/cancel", pending.reservationId())
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Canceled"));
        assertThat(savedStatus(pending.reservationId())).isEqualTo(ReservationStatus.Canceled);
        assertThat(reservationSeats.findAllByReservation_ReservationId(pending.reservationId())).isEmpty();
        assertConflict(() -> prepareApproval(userId, pending.reservationId()), ErrorCode.RESERVATION_NOT_PAYABLE);
        assertThat(create(otherUserId, screeningId, firstSeatId).status()).isEqualTo(ReservationStatus.Pending);
    }

    @Test
    void expiredApprovalIsRejectedBeforeTossAndCleanupReleasesSeats() throws Exception {
        ReservationResponse pending = create(userId, screeningId, firstSeatId);
        PaymentConfirmRequest request = prepareApproval(userId, pending.reservationId());
        backdateExpiry(pending.reservationId());
        mockMvc.perform(post("/api/payments/confirm")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));
        verifyNoInteractions(tossPaymentClient);
        // Approval rejects the request; the expiration service owns seat cleanup.
        assertThat(savedStatus(pending.reservationId())).isEqualTo(ReservationStatus.Pending);
        expirationService.expireReservation(pending.reservationId());
        assertThat(savedStatus(pending.reservationId())).isEqualTo(ReservationStatus.Expired);
        assertThat(reservationSeats.findAllByReservation_ReservationId(pending.reservationId())).isEmpty();
        create(otherUserId, screeningId, firstSeatId);
    }

    @Test
    void newSelectionReleasesWholeExpiredHoldWithoutWaitingForScheduler() {
        ReservationResponse pending = create(userId, screeningId, firstSeatId, secondSeatId);
        backdateExpiry(pending.reservationId());
        create(otherUserId, screeningId, firstSeatId);
        assertThat(savedStatus(pending.reservationId())).isEqualTo(ReservationStatus.Expired);
        assertThat(reservationSeats.findAllByReservation_ReservationId(pending.reservationId())).isEmpty();
        create(otherUserId, screeningId, secondSeatId);
        expirationService.expireReservation(pending.reservationId());
        assertConflict(() -> create(userId, screeningId, firstSeatId), ErrorCode.SEAT_UNAVAILABLE);
    }

    @Test
    void scheduledCleanupExpiresOnlyPendingReservations() {
        ReservationResponse pending = create(userId, screeningId, firstSeatId);
        ReservationResponse confirmed = create(userId, screeningId, secondSeatId);
        confirmReservation(userId, confirmed.reservationId());
        backdateExpiry(pending.reservationId());
        backdateExpiry(confirmed.reservationId());
        scheduler.expirePendingReservations();
        assertThat(savedStatus(pending.reservationId())).isEqualTo(ReservationStatus.Expired);
        assertThat(savedStatus(confirmed.reservationId())).isEqualTo(ReservationStatus.Reserved);
        assertThat(reservationSeats.findAllByReservation_ReservationId(pending.reservationId())).isEmpty();
        assertThat(reservationSeats.findAllByReservation_ReservationId(confirmed.reservationId())).hasSize(1);
    }

    @Test
    void readingAnExpiredHoldReturnsExpiredAndReleasesSeats() {
        ReservationResponse pending = create(userId, screeningId, firstSeatId);
        backdateExpiry(pending.reservationId());
        assertThat(service.getReservation(userId, pending.reservationId()).status()).isEqualTo(ReservationStatus.Expired);
        assertThat(service.getMyReservations(userId).getFirst().seats()).isEmpty();
    }

    @Test
    void canceledPendingReservationCanBeRebookedAndPaidByAnotherUser() {
        ReservationResponse original = create(userId, screeningId, firstSeatId, secondSeatId);
        service.cancelReservation(userId, original.reservationId());

        assertThat(savedStatus(original.reservationId())).isEqualTo(ReservationStatus.Canceled);
        assertThat(reservationSeats.findAllByReservation_ReservationId(original.reservationId())).isEmpty();
        assertRebookedByOtherUser(original.reservationId());
    }

    @Test
    void paidReservationCannotBeCanceledWithoutRefundAndKeepsSeats() {
        ReservationResponse original = create(userId, screeningId, firstSeatId, secondSeatId);
        confirmReservation(userId, original.reservationId());

        assertConflict(() -> service.cancelReservation(userId, original.reservationId()),
                ErrorCode.PAID_RESERVATION_CANCEL_NOT_SUPPORTED);

        assertThat(savedStatus(original.reservationId())).isEqualTo(ReservationStatus.Reserved);
        assertThat(reservationSeats.findAllByReservation_ReservationId(original.reservationId())).hasSize(2);
        assertConflict(() -> create(otherUserId, screeningId, firstSeatId), ErrorCode.SEAT_UNAVAILABLE);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PROCESSING", "UNKNOWN"})
    void unresolvedPaymentPreventsExpirationCancellationAndSeatReassignment(PaymentStatus paymentStatus) {
        ReservationResponse pending = create(userId, screeningId, firstSeatId);
        PaymentConfirmRequest request = prepareApproval(userId, pending.reservationId());
        var context = paymentTransactions.begin(userId, request);
        if (paymentStatus == PaymentStatus.UNKNOWN) {
            assertThat(paymentTransactions.finish(context, null).status()).isEqualTo(PaymentStatus.UNKNOWN);
        }
        backdateExpiry(pending.reservationId());

        scheduler.expirePendingReservations();
        expirationService.expireReservation(pending.reservationId());

        assertThat(savedStatus(pending.reservationId())).isEqualTo(ReservationStatus.Pending);
        assertThat(reservations.findById(pending.reservationId()).orElseThrow().isPaymentProcessing()).isTrue();
        assertThat(reservationSeats.findAllByReservation_ReservationId(pending.reservationId())).hasSize(1);
        assertConflict(() -> service.cancelReservation(userId, pending.reservationId()), ErrorCode.PAYMENT_IN_PROGRESS);
        assertConflict(() -> create(otherUserId, screeningId, firstSeatId), ErrorCode.SEAT_UNAVAILABLE);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void removedDirectConfirmationEndpointCannotBypassPayment() throws Exception {
        ReservationResponse pending = create(userId, screeningId, firstSeatId);
        mockMvc.perform(patch("/api/reservations/{id}/confirm", pending.reservationId())
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userId)))
                .andExpect(status().isNotFound());
        assertThat(savedStatus(pending.reservationId())).isEqualTo(ReservationStatus.Pending);
        verifyNoInteractions(tossPaymentClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"scheduler", "payment-preparation", "new-selection"})
    void expiredReservationCanBeRebookedAndConfirmedByAnotherUser(String expirationTrigger) {
        ReservationResponse original = create(userId, screeningId, firstSeatId, secondSeatId);
        backdateExpiry(original.reservationId());

        switch (expirationTrigger) {
            case "scheduler" -> scheduler.expirePendingReservations();
            case "payment-preparation" -> assertConflict(
                    () -> prepareApproval(userId, original.reservationId()),
                    ErrorCode.RESERVATION_EXPIRED
            );
            case "new-selection" -> { /* 아래의 재선택 요청에서 만료 처리를 검증한다. */ }
            default -> throw new IllegalArgumentException(expirationTrigger);
        }

        assertRebookedByOtherUser(original.reservationId());
        assertThat(savedStatus(original.reservationId())).isEqualTo(ReservationStatus.Expired);
        assertThat(reservationSeats.findAllByReservation_ReservationId(original.reservationId())).isEmpty();
    }

    private void assertRebookedByOtherUser(Long originalReservationId) {
        ReservationResponse replacement = create(otherUserId, screeningId, firstSeatId, secondSeatId);
        assertThat(replacement.reservationId()).isNotEqualTo(originalReservationId);
        assertThat(replacement.status()).isEqualTo(ReservationStatus.Pending);
        assertThat(confirmReservation(otherUserId, replacement.reservationId()).status())
                .isEqualTo(ReservationStatus.Reserved);

        // 이전 예매의 만료 처리가 새 예매의 좌석을 해제하면 안 된다.
        expirationService.expireReservation(originalReservationId);
        assertThat(reservationSeats.findAllByReservation_ReservationId(replacement.reservationId())).hasSize(2);
        assertConflict(() -> create(userId, screeningId, firstSeatId), ErrorCode.SEAT_UNAVAILABLE);
    }

    @Test
    void concurrentSelectionOfSameSeatHasOneWinnerAndNoOrphanReservation() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> selectAfter(start, userId));
            var second = executor.submit(() -> selectAfter(start, otherUserId));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(reservations.findAllByUser_IdOrderByReservationAtDesc(userId).size()
                + reservations.findAllByUser_IdOrderByReservationAtDesc(otherUserId).size()).isEqualTo(1);
    }

    @Test
    void lockingOneReservationDoesNotBlockSelectionOfDifferentSeatInSameScreening() throws Exception {
        ReservationResponse pending = create(userId, screeningId, firstSeatId);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var locker = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                reservations.findByIdWithLock(pending.reservationId()).orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("락 해제 대기 시간 초과");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var otherSelection = executor.submit(() -> create(otherUserId, screeningId, secondSeatId));
                assertThat(otherSelection.get(5, TimeUnit.SECONDS).status()).isEqualTo(ReservationStatus.Pending);
            } finally {
                release.countDown();
            }
            locker.get(5, TimeUnit.SECONDS);
        }
    }

    private boolean selectAfter(CountDownLatch start, Long selectingUserId) throws InterruptedException {
        start.await();
        try {
            create(selectingUserId, screeningId, firstSeatId);
            return true;
        } catch (GlobalException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE);
            return false;
        }
    }

    @Test
    void contenderWaitsForScreeningSeatLockThenSeesCommittedWinningHold() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch finishWinner = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> transaction.execute(ignored -> {
                screeningSeats.findWithLock(screeningId, firstSeatId).orElseThrow();
                locked.countDown();
                await(finishWinner);
                return create(userId, screeningId, firstSeatId);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var contender = executor.submit(() -> {
                contenderStarted.countDown();
                return selectAfter(new CountDownLatch(0), otherUserId);
            });
            try {
                assertThat(contenderStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> contender.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                finishWinner.countDown();
            }
            assertThat(winner.get(10, TimeUnit.SECONDS).status()).isEqualTo(ReservationStatus.Pending);
            assertThat(contender.get(10, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(reservationSeats.findAllByScreening_ScreeningIdAndSeat_SeatIdIn(screeningId, List.of(firstSeatId)))
                .hasSize(1);
        assertThat(reservations.findAllByUser_IdOrderByReservationAtDesc(otherUserId)).isEmpty();
    }

    @Test
    void screeningSeatLockDoesNotBlockOtherSeatOrAnotherScreening() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var locker = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                screeningSeats.findWithLock(screeningId, firstSeatId).orElseThrow();
                locked.countDown();
                await(release);
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var otherSeat = executor.submit(() -> create(userId, screeningId, secondSeatId));
                var otherScreening = executor.submit(() -> create(otherUserId, otherScreeningId, firstSeatId));
                assertThat(otherSeat.get(5, TimeUnit.SECONDS).status()).isEqualTo(ReservationStatus.Pending);
                assertThat(otherScreening.get(5, TimeUnit.SECONDS).status()).isEqualTo(ReservationStatus.Pending);
            } finally {
                release.countDown();
            }
            locker.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void reversedMultiSeatRequestsLockInSameOrderAndHaveOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> selectManyAfter(start, userId, firstSeatId, secondSeatId));
            var second = executor.submit(() -> selectManyAfter(start, otherUserId, secondSeatId, firstSeatId));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(reservationSeats.findAllByScreening_ScreeningIdAndSeat_SeatIdIn(
                screeningId, List.of(firstSeatId, secondSeatId))).hasSize(2);
    }

    private boolean selectManyAfter(CountDownLatch start, Long owner, Long... seats) throws InterruptedException {
        start.await();
        try {
            create(owner, screeningId, seats);
            return true;
        } catch (GlobalException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE);
            return false;
        }
    }

    @Test
    void registeringScreeningCreatesAllLockRowsInSameTransaction() {
        transaction.executeWithoutResult(tx -> {
            var registered = screeningService.createScreening(new ScreeningCreateRequest(
                    screenId, movieId, LocalDate.now().plusDays(8), LocalTime.of(18, 0),
                    LocalTime.of(20, 0), ScreeningType.Original));
            assertThat(jdbc.queryForObject("select count(*) from screening_seat where screening_id = ?",
                    Long.class, registered.screeningId())).isEqualTo(2);
            assertThat(screeningSeats.findWithLock(registered.screeningId(), firstSeatId)).isPresent();
            assertThat(screeningSeats.findWithLock(registered.screeningId(), secondSeatId)).isPresent();
            tx.setRollbackOnly();
        });
    }

    @Test
    void duplicateScreeningSeatRowIsRejectedByUniqueConstraint() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored ->
                screeningSeats.saveAndFlush(ScreeningSeat.create(entityManager.find(Screening.class, screeningId),
                        entityManager.find(Seat.class, firstSeatId)))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("seat lock wait timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private ReservationResponse create(Long selectingUserId, Long targetScreeningId, Long... seatIds) {
        return service.createReservation(selectingUserId, new ReservationCreateRequest(
                targetScreeningId, 0, 0, seatIds.length, 0, List.of(seatIds)
        ));
    }

    private PaymentConfirmRequest prepareApproval(Long payingUserId, Long targetReservationId) {
        PaymentPrepareResponse prepared = paymentService.prepareReservation(payingUserId,
                new ReservationPaymentPrepareRequest(targetReservationId));
        String paymentKey = "test_" + UUID.randomUUID();
        when(tossPaymentClient.confirm(
                eq(new TossPaymentConfirmRequest(paymentKey, prepared.orderId(), prepared.amount())),
                anyString()
        )).thenReturn(new TossPaymentConfirmResponse(
                paymentKey, prepared.orderId(), prepared.amount(), prepared.orderName(),
                "DONE", "카드", OffsetDateTime.now().withNano(0)
        ));
        return new PaymentConfirmRequest(paymentKey, prepared.orderId(), prepared.amount());
    }

    private ReservationResponse confirmReservation(Long payingUserId, Long targetReservationId) {
        paymentConfirmService.confirm(payingUserId, prepareApproval(payingUserId, targetReservationId));
        return service.getReservation(payingUserId, targetReservationId);
    }

    private void backdateExpiry(Long reservationId) {
        jdbc.update("update reservation set expires_at = ? where reservation_id = ?",
                LocalDateTime.now().minusSeconds(1), reservationId);
    }

    private ReservationStatus savedStatus(Long reservationId) {
        return reservations.findById(reservationId).orElseThrow().getStatus();
    }

    private void assertConflict(Runnable operation, ErrorCode expected) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(GlobalException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
