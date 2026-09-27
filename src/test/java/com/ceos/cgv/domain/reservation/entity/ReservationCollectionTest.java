package com.ceos.cgv.domain.reservation.entity;

import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationCollectionTest {
    @Test
    void 외부에서_예매_좌석을_직접_지울_수_없다() {
        Reservation reservation = new Reservation(null, null);
        ReservedSeat seat = new ReservedSeat(reservation, "A", 1, null);
        reservation.addReservedSeat(seat);
        assertThatThrownBy(() -> reservation.getReservedSeats().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(reservation.getReservedSeats()).containsExactly(seat);
    }

    @Test
    void 다른_예매에_속한_자식은_추가할_수_없다() {
        Reservation first = new Reservation(null, null);
        Reservation other = new Reservation(null, null);
        assertThatThrownBy(() -> first.addReservedSeat(new ReservedSeat(other, "A", 1, null)))
                .isInstanceOf(BusinessException.class);
        assertThat(first.getReservedSeats()).isEmpty();
    }

    @Test
    void 좌석_추가는_부모를_연결하고_다른_회차의_좌석을_거절한다() {
        Screening screening = new Screening(null, null, LocalDateTime.of(2030, 1, 1, 12, 0));
        Reservation reservation = new Reservation(null, screening);
        reservation.addSeat("A", 1, new ScreeningSeat(screening, "A", 1));
        assertThat(reservation.getReservedSeats()).singleElement()
                .satisfies(seat -> assertThat(seat.getReservation()).isSameAs(reservation));

        assertThatThrownBy(() -> reservation.addSeat("A", 2,
                new ScreeningSeat(new Screening(null, null, LocalDateTime.of(2030, 1, 1, 15, 0)), "A", 2)))
                .isInstanceOf(BusinessException.class);
        assertThat(reservation.getReservedSeats()).hasSize(1);
    }
}
