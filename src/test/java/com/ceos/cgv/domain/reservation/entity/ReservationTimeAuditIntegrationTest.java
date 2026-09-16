package com.ceos.cgv.domain.reservation.entity;

import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReservationTimeAuditIntegrationTest {

    private static final long USER_ID = 601L;
    private static final long CINEMA_ID = 602L;
    private static final long SCREEN_ID = 603L;
    private static final long MOVIE_ID = 604L;
    private static final long SCREENING_ID = 605L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScreeningRepository screeningRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void 테스트_데이터를_준비한다() {
        jdbcTemplate.update("DELETE FROM screenings WHERE screening_id = ?", SCREENING_ID);
        jdbcTemplate.update("DELETE FROM movies WHERE movie_id = ?", MOVIE_ID);
        jdbcTemplate.update("DELETE FROM screens WHERE screen_id = ?", SCREEN_ID);
        jdbcTemplate.update("DELETE FROM cinemas WHERE cinema_id = ?", CINEMA_ID);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", USER_ID);

        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (?, ?, ?)",
                USER_ID, "시간 테스트 사용자", "reservation-time@example.com");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (?, ?, ?)",
                CINEMA_ID, "시간 테스트 영화관", "서울");
        jdbcTemplate.update("INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row) VALUES (?, ?, ?, ?, ?)",
                SCREEN_ID, CINEMA_ID, "GENERAL", 10, 12);
        jdbcTemplate.update("INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating) VALUES (?, ?, ?, ?, ?, ?)",
                MOVIE_ID, "시간 테스트 영화", "설명", 120, "2026-09-15", "ALL");
        jdbcTemplate.update("INSERT INTO screenings (screening_id, movie_id, screen_id, start_at) VALUES (?, ?, ?, ?)",
                SCREENING_ID, MOVIE_ID, SCREEN_ID, "2026-09-20 12:30:00");
    }

    @AfterEach
    void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM reservations WHERE user_id = ? AND screening_id = ?", USER_ID, SCREENING_ID);
        jdbcTemplate.update("DELETE FROM screenings WHERE screening_id = ?", SCREENING_ID);
        jdbcTemplate.update("DELETE FROM movies WHERE movie_id = ?", MOVIE_ID);
        jdbcTemplate.update("DELETE FROM screens WHERE screen_id = ?", SCREEN_ID);
        jdbcTemplate.update("DELETE FROM cinemas WHERE cinema_id = ?", CINEMA_ID);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", USER_ID);
    }

    @Test
    void 예매를_생성하면_생성일시와_수정일시가_자동으로_기록된다() {
        User user = userRepository.findById(USER_ID).orElseThrow();
        Screening screening = screeningRepository.findById(SCREENING_ID).orElseThrow();

        Reservation reservation = Reservation.builder()
                .user(user)
                .screening(screening)
                .build();

        Reservation saved = reservationRepository.saveAndFlush(reservation);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
