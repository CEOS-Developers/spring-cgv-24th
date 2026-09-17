package com.cgvclone.cgv.domain.cinema_keeping;

import com.cgvclone.cgv.domain.user.User;
import com.cgvclone.cgv.domain.cinema.Cinema;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CinemaKeepingRepository extends JpaRepository<CinemaKeeping, Long> {

    Optional<CinemaKeeping> findByUserAndCinema(User user, Cinema cinema);
}
