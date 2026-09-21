package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieImageCreateRequest;
import com.ceos24.cgv.domain.movie.entity.MovieImage;
import com.ceos24.cgv.domain.movie.enums.MovieImageType;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MovieImageRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieImageServiceTest {

    @Mock
    private MovieImageRepository movieImageRepository;
    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieImageService movieImageService;

    @Test
    void 영화_이미지를_등록한다() {
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie(1L)));
        when(movieImageRepository.save(any(MovieImage.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        Long result = movieImageService.create(1L, new MovieImageCreateRequest("image-url", MovieImageType.POSTER));

        ArgumentCaptor<MovieImage> captor = ArgumentCaptor.forClass(MovieImage.class);
        verify(movieImageRepository).save(captor.capture());
        assertThat(result).isEqualTo(10L);
        assertThat(captor.getValue().getMovieImageUrl()).isEqualTo("image-url");
        assertThat(captor.getValue().getType()).isEqualTo(MovieImageType.POSTER);
    }

    @Test
    void 존재하지_않는_영화에는_이미지를_등록할_수_없다() {
        when(movieRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieImageService.create(1L, new MovieImageCreateRequest("image-url", MovieImageType.POSTER)))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.MOVIE_NOT_FOUND);
    }
}
