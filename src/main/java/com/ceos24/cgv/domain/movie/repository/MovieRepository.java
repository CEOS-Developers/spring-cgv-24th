package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.dto.response.MovieListResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    @Query("""
            select new com.ceos24.cgv.domain.movie.dto.response.MovieListResponse(
                m.id, m.title, m.runningTime, m.openDate, m.status)
            from Movie m
            order by m.openDate desc, m.id desc
            """)
    List<MovieListResponse> findListResponses(Pageable pageable);
    //쿼리메서드 + entitygraph 통해서 추가 쿼리 방지
    //영화 단건 조회시 통계도 함께 가져오기
    @EntityGraph(attributePaths = "movieStatistic")
    Optional<Movie> findWithMovieStatisticById(Long id);
}
