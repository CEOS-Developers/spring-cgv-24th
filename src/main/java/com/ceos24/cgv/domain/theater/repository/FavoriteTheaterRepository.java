package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.entity.FavoriteTheater;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteTheaterRepository extends JpaRepository<FavoriteTheater, Long> {
    boolean existsByTheaterIdAndMemberId(Long theaterId, Long memberId);

    @EntityGraph(attributePaths = {"theater"})
    List<FavoriteTheater> findAllByMemberId(Long memberId);
}
