package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.Movie;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findAllByOrderByOpenDateDescIdDesc();
    //쿼리메서드 + entitygraph 통해서 추가 쿼리 방지
    //영화 단건 조회시 통계도 함께 가져오기
    @EntityGraph(attributePaths = "movieStatistic")
    Optional<Movie> findWithMovieStatisticById(Long id);
}
