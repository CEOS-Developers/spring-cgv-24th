-- Booking ↔ BookingSeat의 showtime 일치 보장을 위한 복합 FK 준비
ALTER TABLE bookings
    ADD CONSTRAINT uk_booking_showtime
        UNIQUE (booking_id, showtime_id);


-- booking_id와 showtime_id 조합이 실제 Booking과 일치해야 함
ALTER TABLE booking_seats
    ADD CONSTRAINT fk_booking_seat_booking_showtime
        FOREIGN KEY (booking_id, showtime_id)
            REFERENCES bookings (booking_id, showtime_id);


-- 현재 점유 중인 좌석인지 자동 계산
ALTER TABLE booking_seats
    ADD COLUMN active_seat TINYINT
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('BOOKED') THEN 1
                ELSE NULL
                END
            ) STORED;


-- 같은 상영 회차의 같은 좌석은 활성 상태로 하나만 존재 가능
CREATE UNIQUE INDEX uk_booking_seat_active_position
    ON booking_seats (
                      showtime_id,
                      row_no,
                      column_no,
                      active_seat
        );