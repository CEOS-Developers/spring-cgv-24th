package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.MoviePerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MoviePersonRepository extends JpaRepository<MoviePerson, Long> {
    List<MoviePerson> findByMovieId(Long movieId);

    @Query("SELECT mp FROM MoviePerson mp JOIN FETCH mp.person WHERE mp.movie.id = :movieId")
    List<MoviePerson> findByMovieIdWithPerson(@Param("movieId") Long movieId);
}
