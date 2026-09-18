package com.cgvclone.cgv.domain.movie_keeping;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.movie.Movie;
import com.cgvclone.cgv.domain.movie.MovieService;
import com.cgvclone.cgv.domain.user.User;
import com.cgvclone.cgv.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MovieKeepingService {

    private final MovieKeepingRepository movieKeepingRepository;
    private final UserService userService;
    private final MovieService movieService;

    @Transactional(readOnly = true)
    public MovieKeeping getMovieKeeping(User user, Movie movie) {
        return movieKeepingRepository.findByUserAndMovie(user, movie)
                .orElseThrow(() -> new GlobalException(ErrorCode.MOVIE_KEEPING_NOT_FOUND));
    }

    public void keepMovie(Long movieId) {
        // TODO: 인증인가 스터디 후 User 지정 필요
        Long currentUserId = 1L;
        User user = userService.getUser(currentUserId);

        Movie movie = movieService.getMovieEntity(movieId);

        validateMovieNotKept(user, movie);

        MovieKeeping movieKeeping = MovieKeeping.builder()
                .user(user)
                .movie(movie)
                .build();

        movieKeepingRepository.save(movieKeeping);
    }

    public void cancelMovieKeeping(Long movieId) {
        // TODO: 인증인가 스터디 후 User 지정 필요
        Long currentUserId = 1L;
        User user = userService.getUser(currentUserId);

        Movie movie = movieService.getMovieEntity(movieId);

        MovieKeeping movieKeeping = getMovieKeeping(user, movie);

        movieKeepingRepository.delete(movieKeeping);
    }

    private void validateMovieNotKept(User user, Movie movie) {
        if (movieKeepingRepository.existsByUserAndMovie(user, movie)) {
            throw new GlobalException(ErrorCode.MOVIE_ALREADY_KEPT);
        }
    }
}
