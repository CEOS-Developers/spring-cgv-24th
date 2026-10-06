package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScreeningSeatBackfillIntegrationTest {
    private static final long SCREENING_ID = 775L;

    @Autowired JdbcTemplate jdbc;
    @Autowired ScreeningSeatBackfillService backfillService;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO users (user_id, name, email) VALUES (771, '이관 회원', 'backfill-771@example.com')");
        jdbc.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (772, '이관 영화관', '서울')");
        jdbc.update("""
                INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row)
                VALUES (773, 772, 'GENERAL', 2, 2)
                """);
        jdbc.update("""
                INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility)
                VALUES (774, '이관 영화', '설명', 120, '2026-09-15', 'ALL', 'PUBLIC')
                """);
        jdbc.update("""
                INSERT INTO screenings (screening_id, movie_id, screen_id, start_at)
                VALUES (775, 774, 773, '2026-09-27 12:30:00')
                """);
    }

    @Test
    void 과거_취소와_현재_예매_이력을_같은_좌석에_연결하고_재실행해도_바뀌지_않는다() {
        reservation(776, "CANCELED");
        reservation(777, "RESERVED");
        reservation(778, "RESERVED");
        history(779, 776, "A", 1);
        history(780, 777, "A", 1);
        history(781, 778, "B", 2);

        assertThat(backfillService.backfill(SCREENING_ID))
                .isEqualTo(new ScreeningSeatBackfillService.BackfillResult(4, 3, 2, 0));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM screening_seats WHERE screening_id = 775
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM reserved_seats rs
                JOIN screening_seats ss ON ss.screening_seat_id = rs.screening_seat_id
                WHERE ss.screening_id = 775 AND rs.seat_row = ss.seat_row
                  AND rs.seat_number = ss.seat_number
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats
                WHERE screening_id = 775 AND seat_row = 'A' AND seat_number = 1
                """, Long.class)).isEqualTo(777L);
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats
                WHERE screening_id = 775 AND seat_row = 'B' AND seat_number = 2
                """, Long.class)).isEqualTo(778L);
        assertThat(jdbc.queryForObject("""
                SELECT screening_seat_id FROM reserved_seats WHERE reserved_seat_id = 779
                """, Long.class)).isEqualTo(jdbc.queryForObject("""
                SELECT screening_seat_id FROM reserved_seats WHERE reserved_seat_id = 780
                """, Long.class));

        assertThat(backfillService.backfill(SCREENING_ID))
                .isEqualTo(new ScreeningSeatBackfillService.BackfillResult(0, 0, 0, 0));
    }

    @Test
    void 같은_좌석의_유효_예매가_둘이면_해당_회차를_롤백한다() {
        reservation(776, "RESERVED");
        reservation(777, "RESERVED");
        history(779, 776, "A", 1);
        history(780, 777, "A", 1);

        assertThatThrownBy(() -> backfillService.backfill(SCREENING_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_MIGRATION_CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM screening_seats WHERE screening_id = 775",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserved_seats WHERE screening_seat_id IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    void 좌석_범위_밖의_이력은_임의로_연결하지_않는다() {
        reservation(776, "RESERVED");
        history(779, 776, "A", 3);

        assertThatThrownBy(() -> backfillService.backfill(SCREENING_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_MIGRATION_CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM screening_seats WHERE screening_id = 775",
                Integer.class)).isZero();
    }

    @Test
    void 일부만_생성된_회차의_좌석은_임의로_채우지_않는다() {
        jdbc.update("""
                INSERT INTO screening_seats (screening_seat_id, screening_id, seat_row, seat_number)
                VALUES (786, 775, 'A', 1)
                """);

        assertThatThrownBy(() -> backfillService.backfill(SCREENING_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_MIGRATION_CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM screening_seats WHERE screening_id = 775",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 재실행은_유효한_HELD와_종료된_RELEASED_이력을_구별한다() {
        jdbc.update("""
                INSERT INTO reservations (reservation_id,user_id,screening_id,status,request_key,expires_at,created_at,updated_at)
                VALUES (776,771,775,'HELD','123e4567-e89b-12d3-a456-426614174020',
                        DATEADD('MINUTE',5,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO reservations (reservation_id,user_id,screening_id,status,request_key,expires_at,created_at,updated_at)
                VALUES (777,771,775,'RELEASED','123e4567-e89b-12d3-a456-426614174021',
                        DATEADD('MINUTE',-5,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        history(779, 776, "A", 1);
        history(780, 777, "B", 2);

        assertThat(backfillService.backfill(SCREENING_ID))
                .isEqualTo(new ScreeningSeatBackfillService.BackfillResult(4, 2, 1, 0));
        assertThat(backfillService.backfill(SCREENING_ID))
                .isEqualTo(new ScreeningSeatBackfillService.BackfillResult(0, 0, 0, 0));
    }

    @Test
    void 연결된_선점이_만료되면_모든_점유를_해제하고_다시_실행할_수_있다() {
        reservation(776, "HELD");
        jdbc.update("UPDATE reservations SET expires_at=DATEADD('MINUTE',5,CURRENT_TIMESTAMP) WHERE reservation_id=776");
        history(779, 776, "A", 1);
        history(780, 776, "B", 2);
        backfillService.backfill(SCREENING_ID);
        jdbc.update("UPDATE reservations SET expires_at=DATEADD('MINUTE',-5,CURRENT_TIMESTAMP) WHERE reservation_id=776");

        assertThat(backfillService.backfill(SCREENING_ID).reservationsExpired()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=776", String.class)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=776", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserved_seats WHERE reservation_id=776 AND screening_seat_id IS NOT NULL", Integer.class)).isEqualTo(2);
        backfillService.backfill(SCREENING_ID);
    }

    @Test
    void 만료_이력이_있어도_다른_예매의_점유는_해제하지_않는다() {
        reservation(776, "HELD");
        jdbc.update("UPDATE reservations SET expires_at=DATEADD('MINUTE',5,CURRENT_TIMESTAMP) WHERE reservation_id=776");
        history(779, 776, "A", 1);
        backfillService.backfill(SCREENING_ID);
        jdbc.update("UPDATE reservations SET expires_at=DATEADD('MINUTE',-5,CURRENT_TIMESTAMP) WHERE reservation_id=776");
        reservation(777, "RESERVED");
        jdbc.update("UPDATE screening_seats SET current_reservation_id=777 WHERE seat_row='A' AND seat_number=1");

        assertThatThrownBy(() -> backfillService.backfill(SCREENING_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.SEAT_MIGRATION_CONFLICT);
        assertThat(jdbc.queryForObject("SELECT current_reservation_id FROM screening_seats WHERE seat_row='A' AND seat_number=1", Long.class)).isEqualTo(777L);
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=776", String.class)).isEqualTo("HELD");
    }

    private void reservation(long id, String status) {
        jdbc.update("""
                INSERT INTO reservations (reservation_id, user_id, screening_id, status, created_at, updated_at)
                VALUES (?, 771, 775, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, status);
    }

    private void history(long id, long reservationId, String row, int number) {
        jdbc.update("""
                INSERT INTO reserved_seats (reserved_seat_id, reservation_id, seat_row, seat_number)
                VALUES (?, ?, ?, ?)
                """, id, reservationId, row, number);
    }
}
