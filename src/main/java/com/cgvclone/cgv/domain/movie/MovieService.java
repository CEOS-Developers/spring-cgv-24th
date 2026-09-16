package com.cgvclone.cgv.domain.movie;

import com.cgvclone.cgv.domain.movie.dto.MovieListResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;

    @Transactional(readOnly = true)
    public MovieListResponse getMovies() {
        List<Movie> movies = movieRepository.findAll();
        return MovieListResponse.from(movies);
    }
}
