package com.cgvclone.cgv.domain.movie_keeping;

import com.cgvclone.cgv.domain.user.User;
import com.cgvclone.cgv.domain.movie.Movie;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieKeepingRepository extends JpaRepository<MovieKeeping, Long> {

    Optional<MovieKeeping> findByUserAndMovie(User user, Movie movie);
}
