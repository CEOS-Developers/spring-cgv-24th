package com.cgvclone.cgv.common.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cgvclone.cgv.domain.User.User;
import com.cgvclone.cgv.domain.User.UserRepository;
import com.cgvclone.cgv.domain.movie.Movie;
import com.cgvclone.cgv.domain.movie.MovieController;
import com.cgvclone.cgv.domain.movie.MovieRepository;
import com.cgvclone.cgv.domain.movie.MovieService;
import com.cgvclone.cgv.domain.movie_keeping.MovieKeepingRepository;
import com.cgvclone.cgv.domain.movie_keeping.MovieKeepingService;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {

    @ParameterizedTest
    @CsvSource({
            "POST, USER_NOT_FOUND, 사용자를 찾을 수 없습니다.",
            "DELETE, USER_NOT_FOUND, 사용자를 찾을 수 없습니다.",
            "POST, MOVIE_NOT_FOUND, 영화를 찾을 수 없습니다.",
            "DELETE, MOVIE_NOT_FOUND, 영화를 찾을 수 없습니다.",
            "DELETE, MOVIE_KEEPING_NOT_FOUND, 해당 영화를 찜 한 적이 없습니다."
    })
    void returnsNotFoundForMissingKeepingResources(String method, ErrorCode errorCode,
            String message) throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        MovieKeepingRepository keepingRepository = mock(MovieKeepingRepository.class);

        if (errorCode != ErrorCode.USER_NOT_FOUND) {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        }
        if (errorCode == ErrorCode.MOVIE_KEEPING_NOT_FOUND) {
            when(movieRepository.findById(10L)).thenReturn(Optional.of(mock(Movie.class)));
        }

        MovieKeepingService keepingService = new MovieKeepingService(
                keepingRepository, userRepository, movieRepository);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new MovieController(mock(MovieService.class), keepingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(request(HttpMethod.valueOf(method), "/movies/10/keep"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(message));

        verify(keepingRepository, never()).save(any());
        verify(keepingRepository, never()).delete(any());
    }
}
