package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.MovieLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieLikeRepository extends JpaRepository<MovieLike, Long> {
    boolean existsByUserIdAndMovieId(Long userId, Long movieId);

    @Query("""
            SELECT ml FROM MovieLike ml
            JOIN FETCH ml.movie
            WHERE ml.user.id = :userId
            ORDER BY ml.id DESC
            """)
    List<MovieLike> findAllByUserIdWithMovie(@Param("userId") Long userId);

    // 파생 deleteBy는 SELECT 후 엔티티별로 지워서, 동시 해제로 이미 사라진 행을 만나면
    // 낙관적 락 예외가 난다. 벌크 DELETE는 0행이어도 정상이라 해제가 멱등해진다.
    @Modifying
    @Query("DELETE FROM MovieLike ml WHERE ml.user.id = :userId AND ml.movie.id = :movieId")
    int deleteByUserIdAndMovieId(@Param("userId") Long userId, @Param("movieId") Long movieId);
}
