package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // 응답은 사용자를 id로만 쓴다. 프록시의 id getter는 초기화 없이 식별자를 돌려주므로
    // user는 조인하지 않는다. 이름 같은 다른 필드를 응답에 실으면 여기에 fetch join을 더해야 한다.
    @Query("""
            SELECT r FROM Reservation r
            JOIN FETCH r.screening s
            JOIN FETCH s.movie
            JOIN FETCH s.theater t
            JOIN FETCH t.branch
            LEFT JOIN FETCH r.seats
            WHERE r.id = :id
            """)
    Optional<Reservation> findByIdWithDetails(@Param("id") Long id);

    // 취소는 좌석 해제와 취소 기한 비교만 한다. 응답을 만들지 않으므로 사용자·영화·지점은 읽지 않는다.
    @Query("""
            SELECT r FROM Reservation r
            JOIN FETCH r.screening
            LEFT JOIN FETCH r.seats
            WHERE r.id = :id
            """)
    Optional<Reservation> findByIdWithSeats(@Param("id") Long id);

    // 좌석을 풀려면 자식까지 손대므로 seats를 함께 가져온다.
    @Query("""
            SELECT r FROM Reservation r
            LEFT JOIN FETCH r.seats
            WHERE r.screening.id = :screeningId
              AND r.status = :pending
              AND r.expiresAt <= :now
            """)
    List<Reservation> findExpiredHolds(@Param("screeningId") Long screeningId,
                                       @Param("pending") ReservationStatus pending,
                                       @Param("now") LocalDateTime now);
}
