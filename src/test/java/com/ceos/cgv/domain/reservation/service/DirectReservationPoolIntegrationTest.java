package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0", "spring.datasource.hikari.connection-timeout=500"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DirectReservationPoolIntegrationTest {
    @Autowired ReservationService reservations;
    @Autowired JdbcTemplate jdbc;
    @Autowired HikariDataSource pool;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO users(user_id,name,email) VALUES(991,'풀 검증','pool@example.test')");
        jdbc.update("INSERT INTO cinemas(cinema_id,name,address) VALUES(992,'영화관','서울')");
        jdbc.update("INSERT INTO screens(screen_id,cinema_id,screen_type,row_count,seats_per_row) VALUES(993,992,'GENERAL',1,1)");
        jdbc.update("INSERT INTO movies(movie_id,title,description,running_time,release_date,age_rating,visibility) VALUES(994,'영화','설명',120,'2026-01-01','ALL','PUBLIC')");
        jdbc.update("INSERT INTO screenings(screening_id,movie_id,screen_id,start_at) VALUES(995,994,993,'2030-01-01 12:00:00')");
        jdbc.update("""
                INSERT INTO reservations(reservation_id,user_id,screening_id,status,request_key,expires_at,created_at,updated_at)
                VALUES(996,991,995,'HELD','123e4567-e89b-12d3-a456-426614174000',
                       DATEADD('MINUTE',-5,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("INSERT INTO screening_seats(screening_seat_id,screening_id,seat_row,seat_number,current_reservation_id) VALUES(997,995,'A',1,996)");
        jdbc.update("INSERT INTO reserved_seats(reserved_seat_id,reservation_id,seat_row,seat_number,screening_seat_id) VALUES(998,996,'A',1,997)");
        // Hibernate 초기화가 끝난 뒤 실제 요청에서 사용할 풀만 한 개로 제한한다.
        pool.setMaximumPoolSize(1);
        pool.getHikariPoolMXBean().softEvictConnections();
        jdbc.queryForObject("SELECT 1", Integer.class);
        assertThat(pool.getHikariPoolMXBean().getTotalConnections()).isEqualTo(1);
    }

    @Test
    void 연결이_하나여도_만료_정리_후_새_예매가_성공한다() {
        var created = reservations.create(request());

        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=996", String.class))
                .isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=997", Long.class))
                .isEqualTo(created.reservationId());
    }

    @Test
    void 유효한_선점은_추가_연결_없이_선점_오류로_거절한다() {
        jdbc.update("UPDATE reservations SET expires_at=DATEADD('MINUTE',5,CURRENT_TIMESTAMP) WHERE reservation_id=996");
        assertRejected(ErrorCode.SEAT_HELD);
    }

    @Test
    void 확정_예매는_추가_연결_없이_중복_예매_오류로_거절한다() {
        jdbc.update("UPDATE reservations SET status='RESERVED' WHERE reservation_id=996");
        assertRejected(ErrorCode.SEAT_ALREADY_RESERVED);
    }

    private void assertRejected(ErrorCode expected) {
        assertThatThrownBy(() -> reservations.create(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(expected);
    }

    private ReservationCreateRequest request() {
        return new ReservationCreateRequest(991L, 995L, List.of(new ReservedSeatRequest("A", 1)));
    }
}
