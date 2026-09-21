package com.ceos.cgv.domain.reservation.repository;

import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

public interface ReservedSeatRepository extends JpaRepository<ReservedSeat, Long>,
        JpaSpecificationExecutor<ReservedSeat> {

    default boolean existsReservedByScreeningIdAndCoordinates(
            Long screeningId, Collection<SeatCoordinate> coordinates) {
        if (coordinates.isEmpty()) {
            return false;
        }
        Specification<ReservedSeat> specification = (root, query, criteriaBuilder) -> {
            Join<ReservedSeat, Reservation> reservation = root.join("reservation");
            Predicate[] requestedSeats = coordinates.stream()
                    .map(seat -> criteriaBuilder.and(
                            criteriaBuilder.equal(root.get("seatRow"), seat.row()),
                            criteriaBuilder.equal(root.get("seatNumber"), seat.number())))
                    .toArray(Predicate[]::new);
            return criteriaBuilder.and(
                    criteriaBuilder.equal(reservation.get("screening").get("id"), screeningId),
                    criteriaBuilder.equal(reservation.get("status"), ReservationStatus.RESERVED),
                    criteriaBuilder.or(requestedSeats));
        };
        return exists(specification);
    }

    @EntityGraph(attributePaths = "reservation")
    List<ReservedSeat> findAllByReservation_Screening_Id(Long screeningId);
}
