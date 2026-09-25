package com.ceos24.cgv.support;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.BranchStatus;
import com.ceos24.cgv.domain.branch.entity.Region;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Stock;
import com.ceos24.cgv.domain.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TestFixtures {

    public static Branch branch(String name) {
        return branch(name, Region.SEOUL, BranchStatus.OPEN);
    }

    public static Branch branch(String name, Region region, BranchStatus status) {
        return Branch.builder()
                .name(name)
                .address("서울시 강남구 테헤란로 1")
                .region(region)
                .status(status)
                .description("""
                        [대중교통]
                        - 지하철 2호선 강남역 11번 출구
                        [주차]
                        - 건물 지하 2~4층, 관람 시 3시간 무료""")
                .imageUrl("https://img.example.com/branch/" + name + ".jpg")
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
        return movie(title, LocalDate.of(2024, 1, 1));
    }

    public static Movie movie(String title, LocalDate releaseDate) {
        return Movie.builder()
                .title(title)
                .director("감독")
                .genre("액션")
                .runningTime(120)
                .releaseDate(releaseDate)
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

    public static Reservation hold(User user, Screening screening, LocalDateTime now) {
        return Reservation.builder()
                .user(user)
                .screening(screening)
                .now(now)
                .build();
    }

    public static Product product(String name, int price) {
        return Product.builder()
                .name(name)
                .price(price)
                .build();
    }

    public static Stock stock(Branch branch, Product product, int quantity) {
        return Stock.builder()
                .branch(branch)
                .product(product)
                .quantity(quantity)
                .build();
    }

    public static User user(String loginId) {
        return User.builder()
                .loginId(loginId)
                .password("pw")
                .name("테스트유저")
                .birthDate(LocalDate.of(2000, 1, 1))
                .email(loginId + "@test.com")
                .phoneNumber("01012345678")
                .build();
    }

    public static User admin(String loginId) {
        return User.createAdmin(loginId, "pw", "관리자", LocalDate.of(2000, 1, 1),
                loginId + "@test.com", "01012345678");
    }
}
