package com.ceos24.cgv.domain.movie.entity;

import com.ceos24.cgv.domain.movie.enums.PersonRole;
import com.ceos24.cgv.domain.person.entity.Person;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoviePerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PersonRole role;

    private MoviePerson(Movie movie, Person person, PersonRole role) {
        this.movie = movie;
        this.person = person;
        this.role = role;
    }

    public static MoviePerson create(Movie movie, Person person, PersonRole role) {
        return new MoviePerson(movie, person, role);
    }
}
