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
    private static final UUID REQUEST_KEY = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-25T09:05:00Z");

    @Test
    void 사용자가_없으면_선점을_생성할_수_없다() {
        assertInvalidHold(null, screening(), REQUEST_KEY, EXPIRES_AT);
    }

    @Test
    void 상영이_없으면_선점을_생성할_수_없다() {
        assertInvalidHold(user(), null, REQUEST_KEY, EXPIRES_AT);
    }

    @Test
    void 요청_키가_없으면_선점을_생성할_수_없다() {
        assertInvalidHold(user(), screening(), null, EXPIRES_AT);
    }

    @Test
    void 만료시각이_없으면_선점을_생성할_수_없다() {
        assertInvalidHold(user(), screening(), REQUEST_KEY, null);
    }

    @Test
    void 선점은_기한_전에_결제를_시작하고_승인_확정은_같은_진행_건을_재사용한다() {
        Reservation hold = hold();

        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(hold.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(hold.getRequestKey()).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        hold.startPayment(EXPIRES_AT.minusMillis(1));
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        hold.confirmPayment();
        hold.confirmPayment();

        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(hold.isExpiredAt(EXPIRES_AT.plusSeconds(60))).isFalse();
    }

    @Test
    void 만료_시각부터는_결제를_시작할_수_없다() {
        Reservation hold = hold();

        assertThatThrownBy(() -> hold.startPayment(EXPIRES_AT))
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
        assertThatThrownBy(() -> released.startPayment(EXPIRES_AT.minusSeconds(1)))
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
        confirmed.startPayment(EXPIRES_AT.minusSeconds(1));
        confirmed.confirmPayment();
        confirmed.cancel();
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    void 결제를_시작하지_않은_선점은_승인_확정할_수_없다() {
        Reservation hold = hold();

        assertThatThrownBy(hold::confirmPayment)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.HOLD_NOT_ACTIVE);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HELD);
    }

    @Test
    void 결제_진행_중에는_선점_기한이_지나도_만료나_해제로_반환하지_않는다() {
        Reservation hold = hold();
        hold.startPayment(EXPIRES_AT.minusMillis(1));

        assertThat(hold.isExpiredAt(EXPIRES_AT.plusSeconds(60))).isFalse();
        assertThatThrownBy(() -> hold.expire(EXPIRES_AT.plusSeconds(60)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.HOLD_NOT_ACTIVE);
        assertThatThrownBy(hold::release)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.HOLD_NOT_ACTIVE);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        hold.confirmPayment();
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    private static Reservation hold() {
        return Reservation.hold(user(), screening(), REQUEST_KEY, EXPIRES_AT);
    }

    private static User user() {
        return new User("회원", "member@example.com");
    }

    private static Screening screening() {
        return new Screening(null, null, LocalDateTime.of(2026, 9, 26, 12, 0));
    }

    private static void assertInvalidHold(User user, Screening screening, UUID requestKey, Instant expiresAt) {
        assertThatThrownBy(() -> Reservation.hold(user, screening, requestKey, expiresAt))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
