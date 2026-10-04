package com.ceos.cgv.domain.reservation.service.payment;

import com.ceos.cgv.domain.movie.dto.SeatAvailabilityResponse;
import com.ceos.cgv.domain.movie.service.ScreeningSeatBackfillService;
import com.ceos.cgv.domain.movie.service.SeatAvailabilityService;
import com.ceos.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldCreationService;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldExpiryService;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldTransitionService;
import com.ceos.cgv.domain.reservation.support.ReservationDatabaseIntegrationTest;
import com.ceos.cgv.domain.reservation.value.SeatCoordinate;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@DirtiesContext
class ReservationPaymentIntegrationTest extends ReservationDatabaseIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(10000);
    private static final Instant NOW = Instant.parse("2030-01-01T01:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ReservationPaymentService payments;
    @Autowired ReservationRepository reservations;
    @Autowired ReservedSeatRepository histories;
    @Autowired SeatHoldCreationService creation;
    @Autowired SeatHoldExpiryService expiry;
    @Autowired SeatHoldTransitionService transitions;
    @Autowired SeatAvailabilityService availability;
    @Autowired ScreeningSeatBackfillService backfill;
    @MockitoBean(name = "seatHoldClock") Clock clock;
    private TransactionTemplate tx;
    private long userId;
    private long movieId;
    private long screeningId;
    private long reservationId;
    private long firstSeatId;
    private long secondSeatId;
    private UUID requestKey;

    @BeforeEach
    void fixture() {
        tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        when(clock.instant()).thenReturn(NOW);
        long base = IDS.getAndAdd(20);
        userId = base;
        movieId = base + 3;
        screeningId = base + 4;
        reservationId = base + 5;
        firstSeatId = base + 9;
        secondSeatId = base + 10;
        requestKey = UUID.randomUUID();
        jdbc.update("INSERT INTO users(user_id,name,email) VALUES(?,'회원',?)", userId, "payment" + base + "@example.test");
        jdbc.update("INSERT INTO cinemas(cinema_id,name,address) VALUES(?,'영화관','서울')", base + 1);
        jdbc.update("INSERT INTO screens(screen_id,cinema_id,screen_type,row_count,seats_per_row) VALUES(?,?,'GENERAL',1,3)", base + 2, base + 1);
        jdbc.update("INSERT INTO movies(movie_id,title,description,running_time,release_date,age_rating,visibility) VALUES(?,'영화','설명',120,'2030-01-01','ALL','PUBLIC')", movieId);
        jdbc.update("INSERT INTO screenings(screening_id,movie_id,screen_id,start_at) VALUES(?,?,?,'2030-01-01 12:00:00')", screeningId, movieId, base + 2);
        jdbc.update("INSERT INTO reservations(reservation_id,user_id,screening_id,status,expires_at,request_key,created_at,updated_at) VALUES(?,?,?,'HELD',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", reservationId, userId, screeningId, Timestamp.from(NOW.plusSeconds(300)), requestKey.toString());
        for (int number = 1; number <= 3; number++) {
            jdbc.update("INSERT INTO screening_seats(screening_seat_id,screening_id,seat_row,seat_number,current_reservation_id) VALUES(?,?,'A',?,?)", base + 5 + number, screeningId, number, number <= 2 ? reservationId : null);
        }
        jdbc.update("INSERT INTO reserved_seats(reserved_seat_id,reservation_id,seat_row,seat_number,screening_seat_id) VALUES(?,?,'A',1,?)", firstSeatId, reservationId, base + 6);
        jdbc.update("INSERT INTO reserved_seats(reserved_seat_id,reservation_id,seat_row,seat_number,screening_seat_id) VALUES(?,?,'A',2,?)", secondSeatId, reservationId, base + 7);
    }

    @Test
    void 호출자_트랜잭션이_필수다() {
        assertThatThrownBy(() -> payments.lockOwned(reservationId, userId)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> payments.startPayment(reservationId, userId)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> payments.confirmPayment(reservationId, userId)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> payments.releaseFailedPayment(reservationId, userId)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> payments.cancelPaidSeats(reservationId, userId, List.of(firstSeatId))).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void 결제_진행_중에는_기한_경과에도_만료와_사용자_해제가_차단된다() {
        start();
        when(clock.instant()).thenReturn(NOW.plusSeconds(400));
        assertThat(expiry.expireIfElapsed(reservationId)).isFalse();
        assertError(() -> transitions.release(reservationId, userId).responseOrThrow(), ErrorCode.HOLD_NOT_ACTIVE);
        assertThat(status()).isEqualTo("PAYMENT_PENDING");
        assertThat(occupant(1)).isEqualTo(reservationId);
        assertThat(availability.findByScreeningId(screeningId).getFirst().status()).isEqualTo(SeatAvailabilityResponse.Status.HELD);
        ReservationStatus visibleStatus = tx.execute(ignored -> ReservationResponse.from(
                reservations.findWithSeatsById(reservationId).orElseThrow(), NOW.plusSeconds(400)).status());
        assertThat(visibleStatus).isEqualTo(ReservationStatus.PAYMENT_PENDING);
    }

    @Test
    void 진행_중인_결제는_같은_키로_재사용하고_새_키의_활성_제한에_포함한다() {
        start();
        when(clock.instant()).thenReturn(NOW.plusSeconds(400));
        assertThat(creation.create(userId, requestKey, request(1, 2)).response().reservationId()).isEqualTo(reservationId);
        assertError(() -> creation.create(userId, UUID.randomUUID(), request(3)), ErrorCode.HOLD_LIMIT_REACHED);
    }

    @Test
    void 승인_완료는_결제_시작_뒤의_영화_숨김이나_선점_기한에_막히지_않는다() {
        start();
        jdbc.update("UPDATE movies SET visibility='HIDDEN' WHERE movie_id=?", movieId);
        when(clock.instant()).thenReturn(NOW.plusSeconds(400));
        tx.executeWithoutResult(ignored -> payments.confirmPayment(reservationId, userId));
        tx.executeWithoutResult(ignored -> payments.confirmPayment(reservationId, userId));
        assertThat(status()).isEqualTo("RESERVED");
        assertThat(occupant(1)).isEqualTo(reservationId);
    }

    @Test
    void 확정_실패는_점유를_해제하며_재호출해도_해제_상태를_유지한다() {
        start();
        tx.executeWithoutResult(ignored -> payments.releaseFailedPayment(reservationId, userId));
        tx.executeWithoutResult(ignored -> payments.releaseFailedPayment(reservationId, userId));
        assertThat(status()).isEqualTo("RELEASED");
        assertThat(occupant(1)).isNull();
        assertThat(occupant(2)).isNull();
    }

    @Test
    void 확정된_예약은_실패_해제로_풀지_않는다() {
        confirm();
        assertError(() -> tx.executeWithoutResult(ignored -> payments.releaseFailedPayment(reservationId, userId)), ErrorCode.HOLD_NOT_ACTIVE);
        assertThat(occupant(1)).isEqualTo(reservationId);
    }

    @Test
    void 부분_취소는_다른_좌석을_보존하고_취소_이력은_재선점과_backfill을_막지_않는다() {
        confirm();
        tx.executeWithoutResult(ignored -> payments.cancelPaidSeats(reservationId, userId, List.of(firstSeatId)));
        assertThat(status()).isEqualTo("RESERVED");
        assertThat(occupant(1)).isNull();
        assertThat(occupant(2)).isEqualTo(reservationId);
        assertThat(histories.existsReservedByScreeningIdAndCoordinates(screeningId, List.of(new SeatCoordinate("A", 1)))).isFalse();
        assertThat(histories.existsReservedByScreeningIdAndCoordinates(screeningId, List.of(new SeatCoordinate("A", 2)))).isTrue();
        long newHold = creation.create(userId, UUID.randomUUID(), request(1)).response().reservationId();
        assertThat(occupant(1)).isEqualTo(newHold);
        backfill.backfill(screeningId);
        assertThat(occupant(1)).isEqualTo(newHold);
        assertThat(occupant(2)).isEqualTo(reservationId);
        PaymentReservationSnapshot snapshot = tx.execute(ignored -> payments.lockOwned(reservationId, userId));
        assertThat(snapshot.seats()).hasSize(2);
        assertThat(snapshot.seats().stream().filter(seat -> seat.reservedSeatId().equals(firstSeatId)).findFirst().orElseThrow().canceledAt()).isEqualTo(NOW);
        ReservationResponse response = tx.execute(ignored -> ReservationResponse.from(reservations.findWithSeatsById(reservationId).orElseThrow()));
        assertThat(response.seats().stream().filter(seat -> seat.reservedSeatId().equals(firstSeatId)).findFirst().orElseThrow().canceled()).isTrue();
    }

    @Test
    void 마지막_활성_좌석까지_취소하면_예약도_취소되지만_이력은_남는다() {
        confirm();
        tx.executeWithoutResult(ignored -> payments.cancelPaidSeats(reservationId, userId, List.of(firstSeatId)));
        tx.executeWithoutResult(ignored -> payments.cancelPaidSeats(reservationId, userId, List.of(secondSeatId)));
        assertThat(status()).isEqualTo("CANCELED");
        assertThat(occupant(2)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserved_seats WHERE reservation_id=? AND canceled_at IS NOT NULL", Integer.class, reservationId)).isEqualTo(2);
    }

    @Test
    void 소유권과_취소_대상과_중복_취소를_검증한다() {
        assertError(() -> tx.executeWithoutResult(ignored -> payments.lockOwned(reservationId, userId + 1)), ErrorCode.ACCESS_DENIED);
        assertError(() -> tx.executeWithoutResult(ignored -> payments.lockOwned(-1L, userId)), ErrorCode.RESERVATION_NOT_FOUND);
        confirm();
        assertError(() -> tx.executeWithoutResult(ignored -> payments.cancelPaidSeats(reservationId, userId, List.of())), ErrorCode.INVALID_REQUEST);
        assertError(() -> tx.executeWithoutResult(ignored -> payments.cancelPaidSeats(reservationId, userId, List.of(firstSeatId, firstSeatId))), ErrorCode.INVALID_REQUEST);
        assertError(() -> tx.executeWithoutResult(ignored -> payments.cancelPaidSeats(reservationId, userId, List.of(-1L))), ErrorCode.INVALID_SEAT);
        tx.executeWithoutResult(ignored -> payments.cancelPaidSeats(reservationId, userId, List.of(firstSeatId)));
        assertError(() -> tx.executeWithoutResult(ignored -> payments.cancelPaidSeats(reservationId, userId, List.of(firstSeatId))), ErrorCode.RESERVATION_ALREADY_CANCELED);
        assertThat(occupant(2)).isEqualTo(reservationId);
    }

    @Test
    void 결제_시작은_만료와_영화_공개_상태와_점유를_검증한다() {
        when(clock.instant()).thenReturn(NOW.plusSeconds(300));
        assertError(this::start, ErrorCode.HOLD_EXPIRED);
        when(clock.instant()).thenReturn(NOW);
        jdbc.update("UPDATE movies SET visibility='HIDDEN' WHERE movie_id=?", movieId);
        assertError(this::start, ErrorCode.MOVIE_NOT_AVAILABLE);
        jdbc.update("UPDATE movies SET visibility='PUBLIC' WHERE movie_id=?", movieId);
        jdbc.update("UPDATE screening_seats SET current_reservation_id=NULL WHERE screening_id=? AND seat_number=1", screeningId);
        assertError(this::start, ErrorCode.SEAT_OWNER_MISMATCH);
        assertThat(status()).isEqualTo("HELD");
    }

    @Test
    void 잠금_전_읽은_상태는_잠금_후_새로_읽고_진행_중_시작은_거부한다() {
        tx.executeWithoutResult(ignored -> {
            assertThat(reservations.findWithSeatsById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.HELD);
            jdbc.update("UPDATE reservations SET status='PAYMENT_PENDING' WHERE reservation_id=?", reservationId);
            assertThat(payments.lockOwned(reservationId, userId).status()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        });
        assertError(this::start, ErrorCode.HOLD_NOT_ACTIVE);
        backfill.backfill(screeningId);
        assertThat(occupant(1)).isEqualTo(reservationId);
    }

    @Test
    void 같은_트랜잭션의_승인과_부분_취소_직후_재조회해도_변경을_보존한다() {
        start();
        tx.executeWithoutResult(ignored -> {
            payments.confirmPayment(reservationId, userId);
            assertThat(payments.lockOwned(reservationId, userId).status()).isEqualTo(ReservationStatus.RESERVED);
            payments.cancelPaidSeats(reservationId, userId, List.of(firstSeatId));
            PaymentReservationSnapshot current = payments.lockOwned(reservationId, userId);
            assertThat(current.status()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(current.seats().stream().filter(seat -> seat.reservedSeatId().equals(firstSeatId))
                    .findFirst().orElseThrow().canceledAt()).isEqualTo(NOW);
        });
        assertThat(status()).isEqualTo("RESERVED");
        assertThat(occupant(1)).isNull();
        assertThat(occupant(2)).isEqualTo(reservationId);
    }

    private void start() { tx.executeWithoutResult(ignored -> payments.startPayment(reservationId, userId)); }
    private void confirm() { start(); tx.executeWithoutResult(ignored -> payments.confirmPayment(reservationId, userId)); }
    private String status() { return jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=?", String.class, reservationId); }
    private Long occupant(int number) { return jdbc.queryForObject("SELECT current_reservation_id FROM screening_seats WHERE screening_id=? AND seat_number=?", Long.class, screeningId, number); }
    private SeatHoldCreateRequest request(int... numbers) {
        return new SeatHoldCreateRequest(screeningId, java.util.Arrays.stream(numbers).mapToObj(number -> new ReservedSeatRequest("A", number)).toList());
    }
    private static void assertError(Runnable operation, ErrorCode errorCode) {
        assertThatThrownBy(operation::run).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(errorCode);
    }
}
