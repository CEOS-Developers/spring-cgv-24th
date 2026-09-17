package com.cgvclone.cgv.domain.movie_keeping;

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
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found"));

        MovieKeeping movieKeeping = MovieKeeping.builder()
                .user(user)
                .movie(movie)
                .build();

        movieKeepingRepository.save(movieKeeping);
    }
}
