package com.ceos24.springboot.favorite.repository;

import com.ceos24.springboot.favorite.domain.MovieLikes;
import com.ceos24.springboot.movie.domain.Movie;
import com.ceos24.springboot.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovieLikesRepository
        extends JpaRepository<MovieLikes, Long> {

    // 중복 찜 확인
    boolean existsByUserAndMovie(User user, Movie movie);

    // 특정 영화 찜 조회
    Optional<MovieLikes> findByUserAndMovie(
            User user,
            Movie movie
    );

    // 사용자가 찜한 영화 전체 조회
    List<MovieLikes> findAllByUser(User user);
}