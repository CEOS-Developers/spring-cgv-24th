package com.cgvclone.cgv.domain.Booking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    @Query("SELECT bs FROM BookingSeat bs " +
            "JOIN bs.booking b " +
            "WHERE b.showtime.showtimeId = :showtimeId " +
            "AND b.status = 'BOOKED'")
    List<BookingSeat> findBookedSeatsByShowtimeId(@Param("showtimeId") Long showtimeId);
}
