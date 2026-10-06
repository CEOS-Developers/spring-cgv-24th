package com.ceos24.spring_cgv.domain.like.repository;

import com.ceos24.spring_cgv.domain.like.entity.CinemaLike;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CinemaLikeRepository extends JpaRepository<CinemaLike, Long> {

    boolean existsByMemberIdAndCinemaId(Long memberId, Long cinemaId);

    Optional<CinemaLike> findByMemberIdAndCinemaId(Long memberId, Long cinemaId);

    @EntityGraph(attributePaths = "cinema")
    List<CinemaLike> findAllByMemberIdOrderByLikedAtDesc(Long memberId);
}
