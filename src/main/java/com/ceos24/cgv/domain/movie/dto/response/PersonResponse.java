package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.MoviePerson;
import com.ceos24.cgv.domain.movie.enums.PersonRole;

public record PersonResponse(
        Long personId,
        String name,
        String personProfileImageUrl,
        PersonRole role
) {
    public static PersonResponse from(MoviePerson moviePerson) {
        return new PersonResponse(
                moviePerson.getPerson().getId(),
                moviePerson.getPerson().getName(),
                moviePerson.getPerson().getPersonProfileImageUrl(),
                moviePerson.getRole()
        );
    }
}
