package com.ceos24.cgv.support;

import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.entity.OrderItem;
import com.ceos24.cgv.domain.concession.entity.Stock;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieImage;
import com.ceos24.cgv.domain.movie.entity.MovieLike;
import com.ceos24.cgv.domain.movie.entity.MoviePerson;
import com.ceos24.cgv.domain.movie.entity.MovieStatistics;
import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieImageType;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import com.ceos24.cgv.domain.movie.enums.PersonRole;
import com.ceos24.cgv.domain.person.entity.Person;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.entity.TheaterLike;
import com.ceos24.cgv.domain.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static User user(Long id) {
        return withId(User.create("user" + id, "password", "사용자", null), id);
    }

    public static Theater theater(Long id) {
        return withId(Theater.create("CGV 강남", "서울", "강남구", "설명", null), id);
    }

    public static Movie movie(Long id) {
        return withId(Movie.create(
                "테스트 영화", "드라마", 120, "영화 설명",
                AgeRating.FIFTEEN, MovieStatus.SHOWING,
                LocalDate.of(2026, 9, 1), null
        ), id);
    }

    public static Screen screen(Long id, Theater theater) {
        ScreenType screenType = withId(ScreenType.create("일반관", 10, 10), 1L);
        return withId(Screen.create(theater, screenType, "1관"), id);
    }

    public static Seat seat(Long id, Screen screen, int rowNum, int colNum) {
        return withId(Seat.create(screen, rowNum, colNum), id);
    }

    public static Schedule schedule(Long id, Movie movie, Screen screen, LocalDateTime startTime) {
        return withId(Schedule.create(movie, screen, startTime, startTime.plusHours(2), 15_000), id);
    }

    public static Item item(Long id, String name, int price) {
        return withId(Item.create(name, price), id);
    }

    public static Stock stock(Long id, Theater theater, Item item, int quantity) {
        return withId(Stock.create(theater, item, quantity), id);
    }

    public static Reservation reservation(Long id, User user, Schedule schedule, String seatSummary) {
        return withId(Reservation.create(user, schedule, schedule.getPrice(), seatSummary), id);
    }

    public static ReservationSeat reservationSeat(Long id, Reservation reservation, Seat seat) {
        return withId(ReservationSeat.create(reservation, seat), id);
    }

    public static MovieImage movieImage(Long id, Movie movie, String url, MovieImageType type) {
        return withId(MovieImage.create(movie, url, type), id);
    }

    public static MovieStatistics movieStatistics(Long id, Movie movie) {
        return withId(MovieStatistics.create(movie, 1_000, 25.5, 90.0, 10), id);
    }

    public static Person person(Long id) {
        return withId(Person.create("배우", null), id);
    }

    public static MoviePerson moviePerson(Long id, Movie movie, Person person) {
        return withId(MoviePerson.create(movie, person, PersonRole.ACTOR), id);
    }

    public static MovieLike movieLike(Long id, User user, Movie movie) {
        return withId(MovieLike.create(user, movie), id);
    }

    public static TheaterLike theaterLike(Long id, User user, Theater theater) {
        return withId(TheaterLike.create(user, theater), id);
    }

    public static Order order(Long id, User user, Theater theater, int totalPrice) {
        return withId(Order.create(user, theater, totalPrice), id);
    }

    public static OrderItem orderItem(Long id, Order order, Item item, int quantity, int price) {
        return withId(OrderItem.create(order, item, quantity, price), id);
    }

    public static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
