package com.ceos.cgv.domain.reservation.entity;

import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationHoldStateTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-25T09:05:00Z");

    @Test
    void 선점은_기한_전에만_확정되고_같은_진행_건을_재사용한다() {
        Reservation hold = hold();

        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(hold.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(hold.getRequestKey()).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        hold.confirm(EXPIRES_AT.minusMillis(1));
        hold.confirm(EXPIRES_AT.plusSeconds(60));

        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(hold.isExpiredAt(EXPIRES_AT.plusSeconds(60))).isFalse();
    }

    @Test
    void 만료_시각부터는_확정할_수_없다() {
        Reservation hold = hold();

        assertThatThrownBy(() -> hold.confirm(EXPIRES_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.HOLD_EXPIRED);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(hold.isExpiredAt(EXPIRES_AT)).isTrue();
    }

    @Test
    void 선점_해제와_만료는_확정_예매_취소와_구별된다() {
        Reservation released = hold();
        released.release();
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThatThrownBy(() -> released.confirm(EXPIRES_AT.minusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.HOLD_NOT_ACTIVE);

        Reservation expired = hold();
        expired.expire(EXPIRES_AT);
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThatThrownBy(expired::cancel)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.HOLD_NOT_ACTIVE);

        Reservation confirmed = hold();
        confirmed.confirm(EXPIRES_AT.minusSeconds(1));
        confirmed.cancel();
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    private static Reservation hold() {
        return Reservation.hold(new User("회원", "member@example.com"),
                new Screening(null, null, LocalDateTime.of(2026, 9, 26, 12, 0)),
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), EXPIRES_AT);
    }
}
