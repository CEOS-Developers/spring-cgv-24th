package com.ceos.cgv.domain.reservation.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatHoldPropertiesTest {
    @Test
    void 선점_시간과_좌석수_동시선점수_정리배치는_양수여야_한다() {
        assertThatThrownBy(() -> new SeatHoldProperties(Duration.ZERO, 8, 1, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeatHoldProperties(Duration.ofMinutes(5), 0, 1, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeatHoldProperties(Duration.ofMinutes(5), 8, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeatHoldProperties(Duration.ofMinutes(5), 8, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
