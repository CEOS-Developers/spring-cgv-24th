package com.ceos24.springboot.reservation;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.movie.domain.AgeRating;
import com.ceos24.springboot.movie.domain.Movie;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.dto.ReservationCreateRequest;
import com.ceos24.springboot.reservation.dto.ReservationResponse;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.service.ReservationService;
import com.ceos24.springboot.reservation.service.ReservationCreateService;
import com.ceos24.springboot.reservation.service.ReservationExpirationService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=3000",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationTransactionIntegrationTest {
    @Autowired private ReservationService service;
    @Autowired private ReservationCreateService createService;
    @Autowired private ReservationExpirationService expirationService;
    @Autowired private ReservationRepository reservations;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private Long userId;
    private Long screeningId;
    private Long seatId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            userId = persist(User.create("예약자", UUID.randomUUID() + "@transaction.test",
                    "unused", "01012345678")).getId();
            Theater theater = persist(Theater.create("트랜잭션 테스트", Region.Seoul));
            Screen screen = persist(Screen.create(theater, "1관", ScreenType.General, 1, 1));
            Movie movie = persist(Movie.builder().titleKr("테스트").runtimeMinutes(120)
                    .ageRating(AgeRating.ALL).build());
            LocalDate date = LocalDate.now(clock).plusDays(7);
            Screening screening = persist(Screening.builder().screen(screen).movie(movie)
                    .screeningDate(date).startTime(LocalTime.NOON).endTime(LocalTime.of(14, 0))
                    .screeningType(ScreeningType.Original).build());
            screeningId = screening.getScreeningId();
            Seat seat = persist(Seat.create(screen, "A", 1));
            seatId = seat.getSeatId();
            persist(ScreeningSeat.create(screening, seat));
            jdbc.update("""
                    insert into price (audience_type, day_type, time_type, screen_category, ticket_price)
                    values ('Adult', ?, 'Normal', 'General', 12000)
                    """, date.getDayOfWeek().getValue() >= 6 ? "Weekend" : "Weekday");
        });
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            for (String table : List.of("reservation_seat", "reservation", "screening_seat",
                    "seat", "screening", "screen", "theater", "movie", "cgv_user", "price")) {
                jdbc.update("delete from " + table);
            }
        });
    }

    @Test
    void expirationAndCreationCompleteWithOnlyOneConnection() {
        ReservationResponse original = create();
        backdateExpiry(original.reservationId());

        ReservationResponse replacement = create();

        assertThat(replacement.status()).isEqualTo(ReservationStatus.Pending);
        assertThat(reservations.findById(original.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.Expired);
        assertThat(service.getReservation(userId, replacement.reservationId()).seats()).hasSize(1);
        assertThat(service.getMyReservations(userId)).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "DETAIL", "LIST"})
    void outerTransactionIsRejectedBeforeExpirationOrCreation(String operation) {
        ReservationResponse original = create();
        backdateExpiry(original.reservationId());

        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            // The only connection is already checked out by this outer transaction.
            assertThat(jdbc.queryForObject("select count(*) from reservation", Long.class)).isEqualTo(1);
            assertThatThrownBy(() -> {
                switch (operation) {
                    case "CREATE" -> create();
                    case "DETAIL" -> service.getReservation(userId, original.reservationId());
                    case "LIST" -> service.getMyReservations(userId);
                    default -> throw new IllegalArgumentException(operation);
                }
            }).isInstanceOf(IllegalTransactionStateException.class);
        });

        assertThat(reservations.findById(original.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.Pending);
        assertThat(jdbc.queryForObject("select count(*) from reservation_seat", Long.class)).isEqualTo(1);
        assertThat(create().status()).isEqualTo(ReservationStatus.Pending);
    }

    @Test
    void anotherRequestWinningAfterExpirationDoesNotLeaveALosingReservation() {
        ReservationResponse original = create();
        backdateExpiry(original.reservationId());
        expirationService.releaseExpiredSeats(screeningId, List.of(seatId));

        // A competing request commits in the gap between expiration and creation.
        ReservationResponse winner = create();
        assertThatThrownBy(() -> createService.createReservation(userId,
                new ReservationCreateRequest(screeningId, 0, 0, 1, 0, List.of(seatId))))
                .isInstanceOfSatisfying(GlobalException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE));

        assertThat(reservations.findById(original.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.Expired);
        assertThat(service.getReservation(userId, winner.reservationId()).seats()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from reservation", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from reservation_seat", Long.class)).isEqualTo(1);
    }

    @Test
    void expirationRemainsCommittedWhenCreationRollsBack() {
        ReservationResponse original = create();
        backdateExpiry(original.reservationId());
        jdbc.update("delete from price");

        assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class);

        assertThat(reservations.findById(original.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.Expired);
        assertThat(jdbc.queryForObject("select count(*) from reservation", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reservation_seat", Long.class)).isZero();
    }

    @Test
    void readsExpireReservationsWithOnlyOneConnectionAndNoOpenSessionInView() {
        ReservationResponse original = create();
        backdateExpiry(original.reservationId());
        assertThat(service.getReservation(userId, original.reservationId()).status())
                .isEqualTo(ReservationStatus.Expired);

        ReservationResponse replacement = create();
        backdateExpiry(replacement.reservationId());
        assertThat(service.getMyReservations(userId))
                .allSatisfy(response -> assertThat(response.status()).isEqualTo(ReservationStatus.Expired));
    }

    @Test
    void tenConcurrentRequestsShareOneConnectionWithoutNestedAcquisition() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(10)) {
            var requests = IntStream.range(0, 10).mapToObj(index -> executor.submit(() -> {
                start.await();
                try {
                    create();
                    return true;
                } catch (GlobalException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE);
                    return false;
                }
            })).toList();
            start.countDown();
            int winners = 0;
            for (var request : requests) {
                if (request.get(20, TimeUnit.SECONDS)) winners++;
            }
            assertThat(winners).isEqualTo(1);
        }
    }

    private ReservationResponse create() {
        return service.createReservation(userId,
                new ReservationCreateRequest(screeningId, 0, 0, 1, 0, List.of(seatId)));
    }

    private void backdateExpiry(Long id) {
        jdbc.update("update reservation set expires_at = ? where reservation_id = ?",
                LocalDateTime.now(clock).minusSeconds(1), id);
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
