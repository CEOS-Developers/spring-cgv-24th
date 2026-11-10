-- Additive and safe to rerun. Run on the local payment-test database.
USE cgv_payment_test;
CREATE TABLE IF NOT EXISTS screening_seat (
    screening_seat_id BIGINT NOT NULL AUTO_INCREMENT,
    screening_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    PRIMARY KEY (screening_seat_id),
    CONSTRAINT uk_screening_seat_slot UNIQUE (screening_id, seat_id),
    CONSTRAINT fk_slot_screening FOREIGN KEY (screening_id) REFERENCES screening(screening_id),
    CONSTRAINT fk_slot_seat FOREIGN KEY (seat_id) REFERENCES seat(seat_id)
) ENGINE=InnoDB;

-- Existing screening/seat IDs, reservations and payments are preserved.
INSERT INTO screening_seat (screening_id, seat_id)
SELECT sc.screening_id, s.seat_id
FROM screening sc JOIN seat s ON s.screen_id = sc.screen_id
WHERE NOT EXISTS (
    SELECT 1 FROM screening_seat ss
    WHERE ss.screening_id = sc.screening_id AND ss.seat_id = s.seat_id
);
