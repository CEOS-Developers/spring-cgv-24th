package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.entity.UserTheater;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTheaterRepository
        extends JpaRepository<UserTheater, Long> {


    boolean existsByUser_IdAndTheater_Id(
            Long userId,
            Long theaterId
    );

    Optional<UserTheater> findByUser_IdAndTheater_Id(
            Long userId,
            Long theaterId
    );
}