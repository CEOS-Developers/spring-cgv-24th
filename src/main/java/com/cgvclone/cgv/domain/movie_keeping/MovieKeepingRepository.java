package com.cgvclone.cgv.domain.movie_keeping;

import com.cgvclone.cgv.domain.movie.Movie;
import com.cgvclone.cgv.domain.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieKeepingRepository extends JpaRepository<MovieKeeping, Long> {

    Optional<MovieKeeping> findByUserAndMovie(User user, Movie movie);

    boolean existsByUserAndMovie(User user, Movie movie);
}
