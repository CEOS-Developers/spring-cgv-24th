package com.cgvclone.cgv.domain.movie_keeping;

import com.cgvclone.cgv.domain.movie.Movie;
import com.cgvclone.cgv.domain.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieKeepingRepository extends JpaRepository<MovieKeeping, Long> {

    @Query("""
            select k.movie from MovieKeeping k
            where k.user.userId = :userId
            order by k.createdAt desc, k.movieKeepingId desc
            """)
    List<Movie> findKeptMovies(@Param("userId") Long userId);

    Optional<MovieKeeping> findByUserAndMovie(User user, Movie movie);

    boolean existsByUserAndMovie(User user, Movie movie);
}
