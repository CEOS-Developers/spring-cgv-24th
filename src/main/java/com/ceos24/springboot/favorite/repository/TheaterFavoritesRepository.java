package com.ceos24.springboot.favorite.repository;

import com.ceos24.springboot.favorite.domain.TheaterFavorites;
import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TheaterFavoritesRepository
        extends JpaRepository<TheaterFavorites, Long> {

    // 중복 찜 확인
    boolean existsByUserAndTheater(
            User user,
            Theater theater
    );

    // 특정 영화관 찜 조회
    Optional<TheaterFavorites> findByUserAndTheater(
            User user,
            Theater theater
    );

    // 사용자가 찜한 영화관 전체 조회
    List<TheaterFavorites> findAllByUser(User user);
}