package com.spring_cgv_24th.domain.favorite.repository;

import com.spring_cgv_24th.domain.favorite.entity.MovieFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface MovieFavoriteRepository extends JpaRepository<MovieFavorite, Long> {

    boolean existsByMember_IdAndMovie_Id(Long memberId, Long movieId);

    Optional<MovieFavorite> findByMember_IdAndMovie_Id(Long memberId, Long movieId);

    @EntityGraph(attributePaths = "movie")
    List<MovieFavorite> findAllByMember_IdOrderByIdDesc(Long memberId);
}
