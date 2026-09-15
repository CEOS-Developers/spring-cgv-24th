package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MoviePersonCreateRequest;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MoviePerson;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MoviePersonRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.person.entity.Person;
import com.ceos24.cgv.domain.person.repository.PersonRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MoviePersonService {

    private final MoviePersonRepository moviePersonRepository;
    private final MovieRepository movieRepository;
    private final PersonRepository personRepository;

    @Transactional
    public Long create(Long movieId, MoviePersonCreateRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new GeneralException(MovieErrorStatus.MOVIE_NOT_FOUND));

        Person person = personRepository.save(Person.create(request.name(), request.personProfileImageUrl()));

        MoviePerson moviePerson = MoviePerson.create(movie, person, request.role());
        return moviePersonRepository.save(moviePerson).getId();
    }
}
