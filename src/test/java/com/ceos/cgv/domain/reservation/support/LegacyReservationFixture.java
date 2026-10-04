package com.ceos.cgv.domain.reservation.support;

import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/** 조회·취소·이관 회귀 검증을 위한 결제 없는 기존 RESERVED 데이터만 만든다. */
public final class LegacyReservationFixture {
    private LegacyReservationFixture() {
    }

    public static long create(JdbcTemplate jdbc, long userId, long screeningId,
                              List<ReservedSeatRequest> seats) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO reservations(user_id,screening_id,status,created_at,updated_at)
                    VALUES(?,?,'RESERVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setLong(2, screeningId);
            return statement;
        }, key);
        long reservationId = key.getKey().longValue();
        for (ReservedSeatRequest seat : seats) {
            List<Long> linkedSeats = jdbc.queryForList("""
                    SELECT screening_seat_id FROM screening_seats
                    WHERE screening_id=? AND seat_row=? AND seat_number=?
                    """, Long.class, screeningId, seat.seatRow(), seat.seatNumber());
            Long linkedSeat = linkedSeats.isEmpty() ? null : linkedSeats.getFirst();
            jdbc.update("""
                    INSERT INTO reserved_seats(reservation_id,seat_row,seat_number,screening_seat_id)
                    VALUES(?,?,?,?)
                    """, reservationId, seat.seatRow(), seat.seatNumber(), linkedSeat);
            if (linkedSeat != null) {
                jdbc.update("UPDATE screening_seats SET current_reservation_id=? WHERE screening_seat_id=?",
                        reservationId, linkedSeat);
            }
        }
        return reservationId;
    }

    /** 선점 재시도·표시 테스트에서 이미 확정된 기존 데이터를 구성한다. 승인을 검증하지 않는다. */
    public static void markReserved(JdbcTemplate jdbc, long reservationId) {
        jdbc.update("UPDATE reservations SET status='RESERVED' WHERE reservation_id=?", reservationId);
    }

    public static void createInventory(JdbcTemplate jdbc, long screeningId, int rows, int seatsPerRow) {
        for (int row = 0; row < rows; row++) {
            for (int number = 1; number <= seatsPerRow; number++) {
                jdbc.update("""
                        INSERT INTO screening_seats(screening_id,seat_row,seat_number) VALUES(?,?,?)
                        """, screeningId, String.valueOf((char) ('A' + row)), number);
            }
        }
    }
}
