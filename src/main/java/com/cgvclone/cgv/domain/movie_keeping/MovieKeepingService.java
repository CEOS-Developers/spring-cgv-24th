package com.cgvclone.cgv.domain.movie_keeping;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.User.User;
import com.cgvclone.cgv.domain.User.UserRepository;
import com.cgvclone.cgv.domain.movie.Movie;
import com.cgvclone.cgv.domain.movie.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MovieKeepingService {

    private final MovieKeepingRepository movieKeepingRepository;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;

    public void keepMovie(Long movieId) {
        // TODO: 인증인가 스터디 후 User 지정 필요
        Long currentUserId = 1L;
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new GlobalException(ErrorCode.USER_NOT_FOUND));

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new GlobalException(ErrorCode.MOVIE_NOT_FOUND));

        MovieKeeping movieKeeping = MovieKeeping.builder()
                .user(user)
                .movie(movie)
                .build();

        movieKeepingRepository.save(movieKeeping);
    }

    public void cancelMovieKeeping(Long movieId) {
        // TODO: 인증인가 스터디 후 User 지정 필요
        Long currentUserId = 1L;
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new GlobalException(ErrorCode.USER_NOT_FOUND));

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new GlobalException(ErrorCode.MOVIE_NOT_FOUND));

        MovieKeeping movieKeeping = movieKeepingRepository.findByUserAndMovie(user, movie)
                .orElseThrow(() -> new GlobalException(ErrorCode.MOVIE_KEEPING_NOT_FOUND));

        movieKeepingRepository.delete(movieKeeping);
    }
}
