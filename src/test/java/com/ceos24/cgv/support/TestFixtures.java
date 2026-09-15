package com.ceos24.cgv.support;

import com.ceos24.cgv.domain.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TestFixtures {

    public static Branch branch(String name) {
        return Branch.builder()
                .name(name)
                .address("서울시 강남구 테헤란로 1")
                .build();
    }

    public static TheaterType theaterType(String name, int rows, int cols) {
        return TheaterType.builder()
                .name(name)
                .rowCount(rows)
                .colCount(cols)
                .build();
    }

    public static Theater theater(Branch branch, TheaterType theaterType, String name) {
        return Theater.builder()
                .branch(branch)
                .theaterType(theaterType)
                .name(name)
                .build();
    }

    public static Movie movie(String title) {
        return Movie.builder()
                .title(title)
                .director("감독")
                .genre("액션")
                .runningTime(120)
                .releaseDate(LocalDate.of(2024, 1, 1))
                .ageRating("12세")
                .build();
    }

    public static Screening screening(Theater theater, Movie movie,
                                      LocalDateTime start, int price) {
        return Screening.builder()
                .theater(theater)
                .movie(movie)
                .startAt(start)
                .endAt(start.plusMinutes(120))
                .price(price)
                .build();
    }

    public static User user(String loginId) {
        return User.builder()
                .loginId(loginId)
                .password("pw")
                .name("테스트유저")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build();
    }
}
