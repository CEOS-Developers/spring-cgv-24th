package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.dto.SeatAvailabilityResponse;
import com.ceos.cgv.domain.movie.dto.SeatAvailabilityResponse.Status;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 좌석 조회가 상영 정보 1회와 좌석 상태 1회, 총 2번의 SQL만 실행하는지 Hibernate 통계로 확인한다.
 * 개선 전에는 상영, 영화, 상영관, 좌석을 각각 읽어 4번 실행했다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@DirtiesContext
class SeatAvailabilityQueryCountIntegrationTest {
    private static final Instant NOW = Instant.parse("2030-01-01T01:00:00Z");
    private static final long SCREENING_ID = 8105L;

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired SeatAvailabilityService seatAvailabilityService;
    @MockitoBean(name = "seatHoldClock") Clock clock;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        jdbc.update("DELETE FROM screening_seats");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("DELETE FROM screenings");
        jdbc.update("DELETE FROM screens");
        jdbc.update("DELETE FROM movies");
        jdbc.update("DELETE FROM cinemas");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users (user_id, name, email) VALUES (8101, '회원', 'seat-count@example.test')");
        jdbc.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (8102, '영화관', '서울')");
    }

    @Test
    void 좌석_조회는_상영_정보와_좌석_상태_두_번의_쿼리만_실행한다() {
        insertScreeningWithSeats("PUBLIC", 3, 2);
        insertSeat(8110, "A", 1, null);
        insertSeat(8111, "A", 2, reservation(8120, "HELD", NOW.plusSeconds(1)));
        insertSeat(8112, "B", 1, reservation(8121, "HELD", NOW.minusSeconds(1)));
        insertSeat(8113, "B", 2, reservation(8122, "HELD", NOW));
        insertSeat(8114, "C", 1, reservation(8123, "RESERVED", null));
        insertSeat(8115, "C", 2, reservation(8124, "PAYMENT_PENDING", NOW.minusSeconds(60)));

        statistics.clear();
        List<SeatAvailabilityResponse> seats = seatAvailabilityService.findByScreeningId(SCREENING_ID);
        long statementCount = statistics.getPrepareStatementCount();
        System.out.printf("seat availability prepared statements=%d%n", statementCount);

        assertThat(statementCount).isEqualTo(2);
        assertThat(seats).containsExactly(
                new SeatAvailabilityResponse("A", 1, Status.AVAILABLE),
                new SeatAvailabilityResponse("A", 2, Status.HELD),
                new SeatAvailabilityResponse("B", 1, Status.AVAILABLE),
                new SeatAvailabilityResponse("B", 2, Status.AVAILABLE),
                new SeatAvailabilityResponse("C", 1, Status.RESERVED),
                new SeatAvailabilityResponse("C", 2, Status.HELD));
    }

    @Test
    void 만료_시각이_정확히_지금이면_AVAILABLE이고_1마이크로초_전이면_HELD다() {
        insertScreeningWithSeats("PUBLIC", 1, 2);
        insertSeat(8110, "A", 1, reservation(8120, "HELD", NOW));
        insertSeat(8111, "A", 2, reservation(8121, "HELD", NOW.plusNanos(1_000)));

        assertThat(seatAvailabilityService.findByScreeningId(SCREENING_ID)).containsExactly(
                new SeatAvailabilityResponse("A", 1, Status.AVAILABLE),
                new SeatAvailabilityResponse("A", 2, Status.HELD));
    }

    @Test
    void 존재하지_않는_상영은_SCREENING_NOT_FOUND이고_쿼리를_한_번만_실행한다() {
        statistics.clear();

        assertThatThrownBy(() -> seatAvailabilityService.findByScreeningId(SCREENING_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SCREENING_NOT_FOUND));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void 비공개_영화의_좌석은_MOVIE_NOT_FOUND이고_좌석_쿼리를_실행하지_않는다() {
        insertScreeningWithSeats("HIDDEN", 1, 1);
        insertSeat(8110, "A", 1, null);

        statistics.clear();

        assertThatThrownBy(() -> seatAvailabilityService.findByScreeningId(SCREENING_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MOVIE_NOT_FOUND));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void 좌석_수가_상영관_정원과_다르면_SCREENING_SEATS_NOT_READY다() {
        insertScreeningWithSeats("PUBLIC", 2, 2);
        insertSeat(8110, "A", 1, null);
        insertSeat(8111, "A", 2, null);
        insertSeat(8112, "B", 1, null);

        assertThatThrownBy(() -> seatAvailabilityService.findByScreeningId(SCREENING_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SCREENING_SEATS_NOT_READY));
    }

    @Test
    void 좌석에_연결된_예약이_종료_상태이면_SCREENING_SEATS_NOT_READY다() {
        insertScreeningWithSeats("PUBLIC", 1, 1);
        insertSeat(8110, "A", 1, reservation(8120, "EXPIRED", NOW.minusSeconds(60)));

        assertThatThrownBy(() -> seatAvailabilityService.findByScreeningId(SCREENING_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SCREENING_SEATS_NOT_READY));
    }

    private void insertScreeningWithSeats(String visibility, int rowCount, int seatsPerRow) {
        jdbc.update("""
                INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row)
                VALUES (8103, 8102, 'GENERAL', ?, ?)
                """, rowCount, seatsPerRow);
        jdbc.update("""
                INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility)
                VALUES (8104, '영화', '설명', 120, '2030-01-01', 'ALL', ?)
                """, visibility);
        jdbc.update("""
                INSERT INTO screenings (screening_id, movie_id, screen_id, start_at)
                VALUES (8105, 8104, 8103, '2030-01-01 12:00:00')
                """);
    }

    private long reservation(long reservationId, String status, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO reservations (reservation_id, user_id, screening_id, status, expires_at,
                                          created_at, updated_at)
                VALUES (?, 8101, 8105, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, reservationId, status, expiresAt == null ? null : Timestamp.from(expiresAt));
        return reservationId;
    }

    private void insertSeat(long seatId, String row, int number, Long reservationId) {
        jdbc.update("""
                INSERT INTO screening_seats (screening_seat_id, screening_id, seat_row, seat_number,
                                             current_reservation_id)
                VALUES (?, 8105, ?, ?, ?)
                """, seatId, row, number, reservationId);
    }
}
