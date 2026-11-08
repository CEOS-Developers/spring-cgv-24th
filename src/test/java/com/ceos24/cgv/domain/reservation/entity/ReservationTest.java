package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReservationTest {

    private static final Instant HELD_AT = Instant.parse("2026-11-03T01:00:00Z");

    private Reservation heldReservation() {
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.of(2026, 11, 3, 18, 0));
        return Reservation.create(user(1L), schedule, 15_000, "A1", HELD_AT);
    }

    @Test
    void 선점_후_4분_59초는_만료되지_않는다() {
        assertThat(heldReservation().isHoldExpired(HELD_AT.plusSeconds(299))).isFalse();
    }

    @Test
    void 선점_후_정확히_5분부터_만료된다() {
        assertThat(heldReservation().isHoldExpired(HELD_AT.plusSeconds(300))).isTrue();
    }

    @Test
    void 결제_완료된_예매는_시간이_지나도_만료되지_않는다() {
        var reservation = heldReservation();
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.RESERVED);

        assertThat(reservation.isHoldExpired(HELD_AT.plusSeconds(3600))).isFalse();
    }

    @Test
    void 결제_진행_중인_예매는_시간이_지나도_만료되지_않는다() {
        var reservation = heldReservation();
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.PAYING);

        assertThat(reservation.isHoldExpired(HELD_AT.plusSeconds(3600))).isFalse();
    }
}
