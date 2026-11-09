package com.ceos24.springboot.payment;

import com.ceos24.springboot.auth.jwt.JwtProvider;
import com.ceos24.springboot.global.exception.*;
import com.ceos24.springboot.movie.domain.*;
import com.ceos24.springboot.payment.domain.*;
import com.ceos24.springboot.payment.dto.*;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.payment.service.PaymentCancelService;
import com.ceos24.springboot.payment.service.PaymentCancelTransactionService;
import com.ceos24.springboot.payment.service.PaymentRecoveryScheduler;
import com.ceos24.springboot.payment.toss.TossPaymentClient;
import com.ceos24.springboot.reservation.domain.*;
import com.ceos24.springboot.shop.domain.*;
import com.ceos24.springboot.theater.domain.*;
import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:payment-cancel-test;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentCancelIntegrationTest {
    @Autowired private Clock clock;
    // 실제 MySQL 검증은 별도로 만든 빈 DB에서만 허용한다. 기본 실행은 H2다.
    @DynamicPropertySource
    static void optionalMySql(DynamicPropertyRegistry registry) {
        String url = System.getenv("PAYMENT_CANCEL_TEST_DB_URL");
        if (url == null) { return; }
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/cgv_payment_cancel_it_[a-z0-9_]+(\\?.*)?")) {
            throw new IllegalArgumentException("취소 통합 테스트는 전용 cgv_payment_cancel_it_* DB에서만 실행할 수 있습니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> System.getenv("PAYMENT_CANCEL_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("PAYMENT_CANCEL_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.hikari.transaction-isolation", () -> "TRANSACTION_REPEATABLE_READ");
    }

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentCancelService service;
    @Autowired private PaymentCancelTransactionService cancellationTransactions;
    @Autowired private PaymentRecoveryScheduler recovery;
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;
    @Autowired private JwtProvider jwt;
    @MockitoBean(enforceOverride = true) private TossPaymentClient toss;

    private static final String REASON = "사용자 요청";
    private TransactionTemplate tx;
    private Long ownerId, otherId, theaterId;
    private Fixture ticket;
    private record Fixture(Long id, Long targetId, long amount, String key, String orderId) {}

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(ignored -> {
            User owner = persist(User.create("취소 구매자", UUID.randomUUID() + "@cancel.test", "unused", "01012345678"));
            ownerId = owner.getId();
            otherId = persist(User.create("다른 구매자", UUID.randomUUID() + "@cancel.test", "unused", "01012345679")).getId();
            Theater theater = persist(Theater.create("취소 테스트", Region.Seoul));
            theaterId = theater.getTheaterId();
            Screen screen = persist(Screen.create(theater, "1관", ScreenType.General, 1, 1));
            Movie movie = persist(Movie.builder().titleKr("취소 테스트").runtimeMinutes(120).ageRating(AgeRating.ALL).build());
            Screening screening = persist(Screening.builder().screen(screen).movie(movie)
                    .screeningDate(LocalDate.now(clock).plusDays(1)).startTime(LocalTime.of(14, 0))
                    .endTime(LocalTime.of(16, 0)).screeningType(ScreeningType.Original).build());
            LocalDateTime now = LocalDateTime.now(clock);
            Reservation reservation = persist(Reservation.builder().user(owner).screening(screening)
                    .childCount(0).youthCount(0).adultCount(1).seniorCount(0).totalPrice(28000)
                    .status(ReservationStatus.Pending).reservationAt(now).expiresAt(now.plusMinutes(10)).build());
            Seat seat = persist(Seat.create(screen, "A", 1));
            persist(ScreeningSeat.create(screening, seat));
            persist(ReservationSeat.create(reservation, screening, seat));
            reservation.beginPayment(now);
            reservation.completePayment();
            ticket = paid(owner, PaymentTargetType.RESERVATION, reservation.getReservationId(), 28000);
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(ignored -> {
            for (String table : List.of("payment_cancellation", "payment", "reservation_seat", "reservation",
                    "screening_seat", "seat", "screening", "screen", "movie", "order_item", "store_order",
                    "store_inventory", "menu", "theater", "cgv_user")) {
                jdbc.update("delete from " + table);
            }
        });
    }

    @Test
    void fullCancellationUpdatesPaymentReleasesSeatsAndRemainsVisibleInHistory() throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenReturn(remote(ticket, entry("full", 28000)));
        postCancel(ticket, "full-request", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelStatus").value("DONE"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELED"))
                .andExpect(jsonPath("$.balanceAmount").value(0));
        assertPayment(ticket, PaymentStatus.CANCELED, 0);
        assertSeats(0);
        assertThat(jdbc.queryForObject("select status from reservation where reservation_id=?", String.class, ticket.targetId()))
                .isEqualTo("Canceled");
        mvc.perform(get("/api/payments/{id}", ticket.id()).header("Authorization", bearer(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(28000))
                .andExpect(jsonPath("$.canceledAmount").value(28000)).andExpect(jsonPath("$.balanceAmount").value(0));
        mvc.perform(get("/api/payments").header("Authorization", bearer(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/payments/{id}/cancellations", ticket.id()).header("Authorization", bearer(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].cancelStatus").value("DONE"))
                .andExpect(jsonPath("$[0].pgIdempotencyKey").doesNotExist())
                .andExpect(jsonPath("$[0].transactionKey").doesNotExist());
        assertThat(jdbc.queryForObject("select transaction_key from payment_cancellation", String.class)).isEqualTo("full");
    }

    @Test
    void multiplePartialCancellationsThenRemainingFullCancellationKeepOriginalAmount() throws Exception {
        var first = entry("first", 8000);
        var second = entry("second", 5000);
        var last = entry("last", 15000);
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenReturn(
                remote(ticket, first), remote(ticket, second, first), remote(ticket, first, second, last));
        postCancel(ticket, "partial-1", 8000L).andExpect(status().isOk());
        assertPayment(ticket, PaymentStatus.PARTIAL_CANCELED, 20000);
        assertSeats(1);
        postCancel(ticket, "partial-2", 5000L).andExpect(status().isOk());
        assertPayment(ticket, PaymentStatus.PARTIAL_CANCELED, 15000);
        assertSeats(1);
        mvc.perform(get("/api/payments/{id}", ticket.id()).header("Authorization", bearer(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canceledAmount").value(13000));
        postCancel(ticket, "remaining", null).andExpect(status().isOk());
        assertPayment(ticket, PaymentStatus.CANCELED, 0);
        assertSeats(0);
        assertThat(jdbc.queryForObject("select count(*) from payment_cancellation where status='DONE'", Long.class)).isEqualTo(3);
        var requests = ArgumentCaptor.forClass(TossPaymentCancelRequest.class);
        verify(toss, times(3)).cancel(eq(ticket.key()), requests.capture(), anyString());
        assertThat(requests.getAllValues()).extracting(TossPaymentCancelRequest::cancelAmount).containsExactly(8000L, 5000L, 15000L);
    }

    @Test
    void repeatedCompletedRequestReturnsSavedResultButNewRequestOnCanceledPaymentIsRejected() throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenReturn(remote(ticket, entry("once", 28000)));
        postCancel(ticket, "same", null).andExpect(status().isOk());
        postCancel(ticket, "same", null).andExpect(status().isOk());
        postCancel(ticket, "new", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_CANCELED"));
        verify(toss, times(1)).cancel(anyString(), any(), anyString());
        assertThat(jdbc.queryForObject("select count(*) from payment_cancellation", Long.class)).isEqualTo(1);
    }

    @Test
    void reusedKeyWithDifferentAmountOrReasonIsRejected() throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenReturn(remote(ticket, entry("first", 8000)));
        postCancel(ticket, "same", 8000L).andExpect(status().isOk());
        postCancel(ticket, "same", 9000L).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_REQUEST_MISMATCH"));
        mvc.perform(post("/api/payments/{id}/cancel", ticket.id()).header("Authorization", bearer(ownerId))
                        .header("Idempotency-Key", "same").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new PaymentCancelRequest("다른 사유", 8000L))))
                .andExpect(status().isConflict());
        verify(toss, times(1)).cancel(anyString(), any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 28001, Long.MAX_VALUE})
    void invalidOrExcessiveAmountNeverCallsPg(long amount) throws Exception {
        postCancel(ticket, "invalid", amount).andExpect(status().isBadRequest());
        verifyNoInteractions(toss);
        assertPayment(ticket, PaymentStatus.DONE, 28000);
    }

    @Test
    void amountMustBeComparedToRemainingBalanceNotOriginalAmount() throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenReturn(remote(ticket, entry("first", 8000)));
        postCancel(ticket, "first", 8000L).andExpect(status().isOk());
        postCancel(ticket, "excess", 21000L).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_AMOUNT_EXCEEDED"));
        verify(toss, times(1)).cancel(anyString(), any(), anyString());
    }

    @Test
    void authenticationOwnershipAndRequiredKeyAreEnforced() throws Exception {
        String body = json.writeValueAsString(new PaymentCancelRequest(REASON, null));
        mvc.perform(post("/api/payments/{id}/cancel", ticket.id()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/{id}/cancel", ticket.id()).header("Authorization", bearer(otherId))
                        .header("Idempotency-Key", "other").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/payments/{id}/cancellations", ticket.id()).header("Authorization", bearer(otherId)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/{id}/cancel", ticket.id()).header("Authorization", bearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAYMENT_CANCEL_REQUEST"));
        verifyNoInteractions(toss);
    }

    @Test
    void readyPaymentCannotBeRefunded() throws Exception {
        tx.executeWithoutResult(ignored -> jdbc.update("update payment set status='READY', approved_at=null, balance_amount=0 where payment_id=?", ticket.id()));
        postCancel(ticket, "ready", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_CANCELABLE"));
        verifyNoInteractions(toss);
    }

    @Test
    void timeoutDefersLookupAndSchedulerCompletesCancellationOnlyOnce() throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenThrow(new ResourceAccessException("timeout"));
        when(toss.findForCancellation(ticket.key())).thenReturn(remote(ticket, entry("recovered", 28000)));
        postCancel(ticket, "lookup", null).andExpect(status().isServiceUnavailable());
        verify(toss, never()).findForCancellation(anyString());
        assertPayment(ticket, PaymentStatus.DONE, 28000);
        assertSeats(1);
        recovery.recoverPayments();
        verify(toss, never()).findForCancellation(anyString());
        makeCancellationRecoveryDue();
        recovery.recoverPayments();
        recovery.recoverPayments();
        assertPayment(ticket, PaymentStatus.CANCELED, 0);
        assertSeats(0);
        verify(toss, times(1)).findForCancellation(ticket.key());
    }

    @Test
    void uncertainResultIsCommittedBlocksNewRequestsAndRetryUsesLookupOnly() throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenThrow(new ResourceAccessException("timeout"));
        when(toss.findForCancellation(ticket.key())).thenReturn(remote(ticket, entry("recovered", 8000)));
        postCancel(ticket, "retry", 8000L).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_RESULT_UNKNOWN"));
        assertThat(jdbc.queryForObject("select status from payment_cancellation", String.class)).isEqualTo("UNKNOWN");
        assertPayment(ticket, PaymentStatus.DONE, 28000);
        assertSeats(1);
        postCancel(ticket, "different", 1000L).andExpect(status().isConflict());
        postCancel(ticket, "retry", 8000L).andExpect(status().isOk());
        assertPayment(ticket, PaymentStatus.PARTIAL_CANCELED, 20000);
        var keys = ArgumentCaptor.forClass(String.class);
        verify(toss, times(1)).cancel(eq(ticket.key()), any(), keys.capture());
        verify(toss, times(1)).findForCancellation(ticket.key());
        assertThat(jdbc.queryForObject("select count(*) from payment_cancellation", Long.class)).isEqualTo(1);
    }

    @Test
    void explicitRejectionAndUnchangedLookupRecordsFailureWithoutChangingMoney() throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenThrow(
                new HttpClientErrorException(HttpStatus.BAD_REQUEST));
        when(toss.findForCancellation(ticket.key())).thenReturn(remote(ticket));
        postCancel(ticket, "rejected", 8000L).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_RESULT_UNKNOWN"));
        verify(toss, never()).findForCancellation(anyString());
        makeCancellationRecoveryDue();
        recovery.recoverPayments();
        postCancel(ticket, "rejected", 8000L).andExpect(status().isBadGateway());
        verify(toss, times(1)).cancel(anyString(), any(), anyString());
        assertThat(jdbc.queryForObject("select status from payment_cancellation", String.class)).isEqualTo("FAILED");
        assertPayment(ticket, PaymentStatus.DONE, 28000);
        assertSeats(1);
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenReturn(remote(ticket, entry("next", 8000)));
        postCancel(ticket, "new-after-rejection", 8000L).andExpect(status().isOk());
        assertPayment(ticket, PaymentStatus.PARTIAL_CANCELED, 20000);
    }

    @ParameterizedTest
    @ValueSource(ints = {409, 429, 500})
    void pendingOrServerErrorsWithUnchangedLookupRemainUnknown(int status) throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenThrow(
                new RestClientResponseException("PG error", status, "error", null, null, null));
        when(toss.findForCancellation(ticket.key())).thenReturn(remote(ticket));
        postCancel(ticket, "pending", 8000L).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select status from payment_cancellation", String.class)).isEqualTo("UNKNOWN");
        assertPayment(ticket, PaymentStatus.DONE, 28000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "order", "amount", "currency", "balance", "reason", "pending", "duplicate"})
    void mismatchedOrIncompletePgEvidenceCannotChangeMoneyOrSeats(String field) throws Exception {
        var valid = entry("tx", 8000);
        var cancel = switch (field) {
            case "reason" -> new TossPaymentCancelResponse.Cancel("tx", 8000L, "다른 사유", "DONE", valid.canceledAt());
            case "pending" -> new TossPaymentCancelResponse.Cancel("tx", 8000L, REASON, "IN_PROGRESS", valid.canceledAt());
            default -> valid;
        };
        var response = new TossPaymentCancelResponse(field.equals("key") ? "wrong" : ticket.key(),
                field.equals("order") ? "wrong" : ticket.orderId(), field.equals("amount") ? 29000L : 28000L,
                field.equals("currency") ? "USD" : "KRW", "PARTIAL_CANCELED",
                field.equals("balance") ? 19000L : 20000L,
                field.equals("duplicate") ? List.of(cancel, cancel) : List.of(cancel));
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenReturn(response);
        postCancel(ticket, "mismatch", 8000L).andExpect(status().isServiceUnavailable());
        assertPayment(ticket, PaymentStatus.DONE, 28000);
        assertSeats(1);
    }

    @Test
    void oldUncertainRequestUsesLookupWithoutRepostingExpiredPgIdempotencyKey() throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenThrow(new ResourceAccessException("timeout"));
        when(toss.findForCancellation(ticket.key())).thenThrow(new ResourceAccessException("offline"));
        postCancel(ticket, "old", 8000L).andExpect(status().isServiceUnavailable());
        jdbc.update("update payment_cancellation set created_at=?", LocalDateTime.now(clock).minusDays(16));
        doReturn(remote(ticket, entry("late", 8000))).when(toss).findForCancellation(ticket.key());
        postCancel(ticket, "old", 8000L).andExpect(status().isOk());
        verify(toss, times(1)).cancel(anyString(), any(), anyString());
        assertPayment(ticket, PaymentStatus.PARTIAL_CANCELED, 20000);
    }

    @Test
    void concurrentSameRequestCanReachPgWithoutDbLocksAndAppliesRefundOnlyOnce() throws Exception {
        CountDownLatch bothAtPg = new CountDownLatch(2);
        Set<String> keys = ConcurrentHashMap.newKeySet();
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            keys.add(invocation.getArgument(2));
            bothAtPg.countDown();
            assertThat(bothAtPg.await(10, TimeUnit.SECONDS)).isTrue();
            return remote(ticket, entry("concurrent", 8000));
        });
        when(toss.findForCancellation(ticket.key())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            bothAtPg.countDown();
            assertThat(bothAtPg.await(10, TimeUnit.SECONDS)).isTrue();
            return remote(ticket, entry("concurrent", 8000));
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.cancel(ownerId, ticket.id(), "same-concurrent", new PaymentCancelRequest(REASON, 8000L)));
            var second = executor.submit(() -> service.cancel(ownerId, ticket.id(), "same-concurrent", new PaymentCancelRequest(REASON, 8000L)));
            assertThat(first.get(15, TimeUnit.SECONDS).cancelStatus()).isEqualTo(PaymentCancellationStatus.DONE);
            assertThat(second.get(15, TimeUnit.SECONDS).cancelStatus()).isEqualTo(PaymentCancellationStatus.DONE);
        }
        assertThat(keys).hasSize(1);
        assertPayment(ticket, PaymentStatus.PARTIAL_CANCELED, 20000);
        assertThat(jdbc.queryForObject("select count(*) from payment_cancellation", Long.class)).isEqualTo(1);
    }

    @Test
    void crashedCancellationAndExpiredLeaseRecoverWithoutDoubleRefund() {
        var context = cancellationTransactions.begin(ownerId, ticket.id(), "crashed",
                new PaymentCancelRequest(REASON, 8000L));
        recovery.recoverPayments();
        verifyNoInteractions(toss);
        makeCancellationRecoveryDue();
        var abandoned = cancellationTransactions.claimRecovery(context.cancellationId()).orElseThrow();
        assertThat(cancellationTransactions.claimRecovery(context.cancellationId())).isEmpty();
        makeCancellationRecoveryDue();
        when(toss.findForCancellation(ticket.key())).thenAnswer(ignored -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return remote(ticket, entry("after-crash", 8000));
        });
        recovery.recoverPayments();
        cancellationTransactions.finish(abandoned, remote(ticket, entry("after-crash", 8000)), false);
        recovery.recoverPayments();
        assertPayment(ticket, PaymentStatus.PARTIAL_CANCELED, 20000);
        assertSeats(1);
        assertThat(jdbc.queryForObject("select recovery_attempts from payment_cancellation", Integer.class)).isEqualTo(2);
        verify(toss, times(1)).findForCancellation(ticket.key());
        verify(toss, never()).cancel(anyString(), any(), anyString());
    }

    @Test
    void unchangedCancellationLookupCannotProveTimedOutRequestFailed() throws Exception {
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenThrow(new ResourceAccessException("timeout"));
        when(toss.findForCancellation(ticket.key())).thenReturn(remote(ticket));
        postCancel(ticket, "uncertain-no-change", 8000L).andExpect(status().isServiceUnavailable());
        makeCancellationRecoveryDue();
        recovery.recoverPayments();
        assertThat(jdbc.queryForObject("select status from payment_cancellation", String.class)).isEqualTo("UNKNOWN");
        assertPayment(ticket, PaymentStatus.DONE, 28000);
        assertSeats(1);
        assertThat(jdbc.queryForObject("select next_recovery_at from payment_cancellation", LocalDateTime.class))
                .isAfter(LocalDateTime.now(clock));
    }

    private void makeCancellationRecoveryDue() {
        jdbc.update("update payment_cancellation set next_recovery_at=?", LocalDateTime.now(clock).minusMinutes(1));
    }

    @Test
    void differentConcurrentRequestIsBlockedWhileFirstPgCallIsOutstanding() throws Exception {
        CountDownLatch atPg = new CountDownLatch(1);
        CountDownLatch releasePg = new CountDownLatch(1);
        when(toss.cancel(eq(ticket.key()), any(), anyString())).thenAnswer(invocation -> {
            atPg.countDown();
            assertThat(releasePg.await(10, TimeUnit.SECONDS)).isTrue();
            return remote(ticket, entry("serialized", 8000));
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.cancel(ownerId, ticket.id(), "in-flight", new PaymentCancelRequest(REASON, 8000L)));
            try {
                assertThat(atPg.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.cancel(ownerId, ticket.id(), "different", new PaymentCancelRequest(REASON, 24000L)))
                        .isInstanceOfSatisfying(GlobalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS));
            } finally { releasePg.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).cancelStatus()).isEqualTo(PaymentCancellationStatus.DONE);
        }
        verify(toss, times(1)).cancel(anyString(), any(), anyString());
    }

    @Test
    void storeOrderKeepsStockForPartialRefundAndRestoresItOnceAtZeroBalance() throws Exception {
        Fixture store = storeFixture();
        var partial = entry("store-partial", 4000);
        var remaining = entry("store-full", 8000);
        when(toss.cancel(eq(store.key()), any(), anyString())).thenReturn(remote(store, partial), remote(store, partial, remaining));
        postCancel(store, "store-partial", 4000L).andExpect(status().isOk());
        assertThat(stock()).isEqualTo(8);
        assertThat(jdbc.queryForObject("select status from store_order", String.class)).isEqualTo("PAID");
        postCancel(store, "store-full", null).andExpect(status().isOk());
        postCancel(store, "store-full", null).andExpect(status().isOk());
        assertThat(stock()).isEqualTo(10);
        assertThat(jdbc.queryForObject("select status from store_order", String.class)).isEqualTo("CANCELED");
        assertPayment(store, PaymentStatus.CANCELED, 0);
        verify(toss, times(2)).cancel(eq(store.key()), any(), anyString());
    }

    @Test
    void dbFailureAfterPgSuccessRollsBackLocalStateAndSameRequestCanRecover() throws Exception {
        Fixture store = storeFixture();
        when(toss.cancel(eq(store.key()), any(), anyString())).thenReturn(remote(store, entry("recover-db", 12000)));
        jdbc.update("update store_inventory set stock=?", Integer.MAX_VALUE);
        assertThatThrownBy(() -> service.cancel(ownerId, store.id(), "db-retry", new PaymentCancelRequest(REASON, null)))
                .isInstanceOf(ArithmeticException.class);
        assertPayment(store, PaymentStatus.DONE, 12000);
        assertThat(jdbc.queryForObject("select status from payment_cancellation", String.class)).isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("select status from store_order", String.class)).isEqualTo("PAID");
        jdbc.update("update store_inventory set stock=8");
        when(toss.findForCancellation(store.key())).thenReturn(remote(store, entry("recover-db", 12000)));
        postCancel(store, "db-retry", null).andExpect(status().isOk());
        assertThat(stock()).isEqualTo(10);
        var keys = ArgumentCaptor.forClass(String.class);
        verify(toss, times(1)).cancel(eq(store.key()), any(), keys.capture());
        verify(toss, times(1)).findForCancellation(store.key());
    }

    private org.springframework.test.web.servlet.ResultActions postCancel(Fixture fixture, String requestId, Long amount) throws Exception {
        return mvc.perform(post("/api/payments/{id}/cancel", fixture.id()).header("Authorization", bearer(ownerId))
                .header("Idempotency-Key", requestId).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new PaymentCancelRequest(REASON, amount))));
    }

    private TossPaymentCancelResponse.Cancel entry(String key, long amount) {
        return new TossPaymentCancelResponse.Cancel(key, amount, REASON, "DONE", OffsetDateTime.now(ZoneOffset.ofHours(9)));
    }

    private TossPaymentCancelResponse remote(Fixture fixture, TossPaymentCancelResponse.Cancel... entries) {
        long balance = fixture.amount() - Arrays.stream(entries).mapToLong(TossPaymentCancelResponse.Cancel::cancelAmount).sum();
        String status = balance == 0 ? "CANCELED" : balance == fixture.amount() ? "DONE" : "PARTIAL_CANCELED";
        return new TossPaymentCancelResponse(fixture.key(), fixture.orderId(), fixture.amount(), "KRW", status, balance, List.of(entries));
    }

    private Fixture paid(User owner, PaymentTargetType type, Long id, long amount) {
        LocalDateTime now = LocalDateTime.now(clock);
        Payment payment = persist(Payment.prepare(owner, type, id, amount, now, now.plusMinutes(10)));
        payment.startProcessing("cancel-test-" + payment.getId());
        payment.complete(now, "카드");
        return new Fixture(payment.getId(), id, amount, payment.getPaymentKey(), payment.getOrderId());
    }

    private Fixture storeFixture() {
        return tx.execute(ignored -> {
            User owner = em.find(User.class, ownerId);
            Theater theater = em.find(Theater.class, theaterId);
            jdbc.update("insert into menu(menu_name, menu_price) values ('cancel-test', 6000)");
            Menu menu = em.find(Menu.class, jdbc.queryForObject("select menu_id from menu where menu_name='cancel-test'", Long.class));
            persist(StoreInventory.builder().theater(theater).menu(menu).stock(8).build());
            StoreOrder order = persist(StoreOrder.builder().user(owner).theater(theater).totalAmount(12000).now(LocalDateTime.now(clock)).build());
            persist(OrderItem.builder().order(order).menu(menu).quantity(2).build());
            order.beginPayment(LocalDateTime.now(clock));
            order.completePayment();
            return paid(owner, PaymentTargetType.STORE_ORDER, order.getOrderId(), 12000);
        });
    }

    private int stock() { return jdbc.queryForObject("select stock from store_inventory", Integer.class); }
    private void assertSeats(int count) { assertThat(jdbc.queryForObject("select count(*) from reservation_seat", Integer.class)).isEqualTo(count); }
    private void assertPayment(Fixture fixture, PaymentStatus state, long balance) {
        Payment payment = payments.findById(fixture.id()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(state);
        assertThat(payment.getAmount()).isEqualTo(fixture.amount());
        assertThat(payment.getBalanceAmount()).isEqualTo(balance);
    }
    private String bearer(Long id) { return "Bearer " + jwt.createAccessToken(id); }
    private <T> T persist(T entity) { em.persist(entity); return entity; }
}
