package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MoviePersonCreateRequest;
import com.ceos24.cgv.domain.movie.entity.MoviePerson;
import com.ceos24.cgv.domain.movie.enums.PersonRole;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MoviePersonRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.person.entity.Person;
import com.ceos24.cgv.domain.person.repository.PersonRepository;
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
class MoviePersonServiceTest {

    @Mock
    private MoviePersonRepository moviePersonRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private MoviePersonService moviePersonService;

    @Test
    void 영화에_감독_또는_배우를_등록한다() {
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie(1L)));
        when(personRepository.save(any(Person.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 20L));
        when(moviePersonRepository.save(any(MoviePerson.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 30L));

        Long result = moviePersonService.create(1L, new MoviePersonCreateRequest("홍길동", "profile", PersonRole.DIRECTOR));

        ArgumentCaptor<MoviePerson> captor = ArgumentCaptor.forClass(MoviePerson.class);
        verify(moviePersonRepository).save(captor.capture());
        assertThat(result).isEqualTo(30L);
        assertThat(captor.getValue().getPerson().getId()).isEqualTo(20L);
        assertThat(captor.getValue().getRole()).isEqualTo(PersonRole.DIRECTOR);
    }

    @Test
    void 존재하지_않는_영화에는_인물을_등록할_수_없다() {
        when(movieRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moviePersonService.create(1L, new MoviePersonCreateRequest("홍길동", null, PersonRole.ACTOR)))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.MOVIE_NOT_FOUND);
        verifyNoInteractions(personRepository, moviePersonRepository);
    }
}
