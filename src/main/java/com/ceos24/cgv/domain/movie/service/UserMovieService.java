package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.UserMovie;
import com.ceos24.cgv.domain.movie.exception.MovieErrorCode;
import com.ceos24.cgv.domain.movie.exception.MovieException;
import com.ceos24.cgv.domain.movie.exception.UserMovieErrorCode;
import com.ceos24.cgv.domain.movie.exception.UserMovieException;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.movie.repository.UserMovieRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorCode;
import com.ceos24.cgv.domain.user.exception.UserException;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserMovieService {

    private final UserMovieRepository userMovieRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;

    /**
     * 영화 찜 등록
     */
    @Transactional
    public void likeMovie(
            Long userId,
            Long movieId
    ) {
        User user = getUser(userId);
        Movie movie = getMovie(movieId);

        boolean alreadyLiked =
                userMovieRepository
                        .existsByUser_IdAndMovie_Id(
                                userId,
                                movieId
                        );

        if (alreadyLiked) {
            throw new UserMovieException(
                    UserMovieErrorCode.MOVIE_ALREADY_LIKED
            );
        }

        UserMovie userMovie = UserMovie.builder()
                .user(user)
                .movie(movie)
                .build();

        userMovieRepository.save(userMovie);
    }

    /**
     * 영화 찜 취소
     */
    @Transactional
    public void unlikeMovie(
            Long userId,
            Long movieId
    ) {
        UserMovie userMovie =
                userMovieRepository
                        .findByUser_IdAndMovie_Id(
                                userId,
                                movieId
                        )
                        .orElseThrow(() ->
                                new UserMovieException(
                                        UserMovieErrorCode
                                                .MOVIE_LIKE_NOT_FOUND
                                )
                        );

        userMovieRepository.delete(userMovie);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserException(
                                UserErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private Movie getMovie(Long movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() ->
                        new MovieException(
                                MovieErrorCode.MOVIE_NOT_FOUND
                        )
                );
    }
}