package com.ceos.cgv.domain.movie.repository;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.enums.MovieVisibility;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    List<Movie> findAllByVisibility(MovieVisibility visibility);

    Optional<Movie> findByIdAndVisibility(Long id, MovieVisibility visibility);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Movie m where m.id = :movieId")
    Optional<Movie> findByIdForUpdate(@Param("movieId") Long movieId);
}
