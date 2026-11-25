package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.MovieLike;
import com.ceos.cgv.domain.movie.repository.MovieLikeRepository;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MovieLikeService {
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final MovieLikeRepository movieLikeRepository;

    // 같은 회원의 동시 토글(더블 클릭, 재시도)을 회원 행 잠금으로 직렬화한다.
    // 직렬화하지 않으면 두 요청이 모두 '찜 없음'으로 읽고 삽입해 유니크 제약 위반(500)이 난다.
    // 잠금 뒤의 조회가 앞선 트랜잭션의 커밋 결과를 보도록 READ_COMMITTED를 쓴다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean toggle(Long userId, Long movieId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Movie movie = movieRepository.findByIdForShare(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));

        Optional<MovieLike> existingLike = movieLikeRepository.findByUser_IdAndMovie_Id(userId, movieId);
        if (existingLike.isPresent()) {
            movieLikeRepository.delete(existingLike.get());
            return false;
        }

        movie.ensurePublic();
        movieLikeRepository.save(MovieLike.builder()
                .user(user)
                .movie(movie)
                .build());
        return true;
    }
}
