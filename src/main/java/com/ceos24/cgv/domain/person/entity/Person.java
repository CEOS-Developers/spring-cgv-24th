package com.ceos24.cgv.domain.person.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String personProfileImageUrl;

    private Person(String name, String personProfileImageUrl) {
        this.name = name;
        this.personProfileImageUrl = personProfileImageUrl;
    }

    public static Person create(String name, String personProfileImageUrl) {
        return new Person(name, personProfileImageUrl);
    }
}
