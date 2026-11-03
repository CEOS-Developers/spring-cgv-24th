package com.ceos24.cgv.domain.payment.fixture;

import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumType;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumTypeCategory;
import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class PaymentTestFixtures {
    private PaymentTestFixtures() { }

    public static Reservation persistReservation(EntityManager em, UserEntity user, Cinema cinema) {
        String suffix = UUID.randomUUID().toString();
        AuditoriumType type = AuditoriumType.create("type-" + suffix, AuditoriumTypeCategory.NORMAL, 1, 1);
        em.persist(type);
        Auditorium auditorium = Auditorium.create(cinema, type, "auditorium-" + suffix);
        em.persist(auditorium);
        Movie movie = Movie.create("결제 테스트 영화", LocalDate.now());
        em.persist(movie);
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1);
        Screening screening = Screening.create(auditorium, movie, startsAt, startsAt.plusHours(2), 15_000);
        em.persist(screening);
        Reservation reservation = Reservation.create(screening, user);
        em.persist(reservation);
        return reservation;
    }
}
