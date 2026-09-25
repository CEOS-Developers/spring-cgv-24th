package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest.SeatRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 응답 DTO가 읽는 연관을 쿼리가 다 가져오는지 확인한다. 필드가 늘어 LAZY 초기화가
// 다시 끼어들면 SQL 수가 어긋나 여기서 잡힌다.
@SpringBootTest
@Transactional
class ReservationQueryCountTest {

    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired ReservationService reservationService;
    @Autowired Clock clock;

    private Statistics statistics;
    private Long screeningId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Branch branch = TestFixtures.branch("강남");
        Theater theater = TestFixtures.theater(branch, TheaterType.STANDARD, "1관");
        Movie movie = TestFixtures.movie("범죄도시4");
        Screening screening = TestFixtures.screening(
                theater, movie, LocalDateTime.now(clock).plusDays(1), 14000);
        User user = TestFixtures.user("queryuser1");

        em.persist(branch);
        em.persist(theater);
        em.persist(movie);
        em.persist(screening);
        em.persist(user);
        em.flush();
        em.clear();

        screeningId = screening.getId();
        userId = user.getId();

        statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void 좌석_선점은_SQL_7회로_끝난다() {
        ReservationResponse response = reservationService.create(userId, requestOf(
                new SeatRequest(1, 1, AudienceType.ADULT),
                new SeatRequest(1, 2, AudienceType.YOUTH)));

        // SELECT 4: 회차(+영화·상영관·지점) / 사용자 / 만료 선점 / 점유 좌석
        // INSERT 3: reservation 1 + reservation_seat 2
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(7);
        assertThat(response.screening().movieTitle()).isEqualTo("범죄도시4");
        assertThat(response.screening().branchName()).isEqualTo("강남");
    }

    @Test
    void 예매_단건_조회는_SQL_1회로_끝난다() {
        Long reservationId = reservationService.create(userId, requestOf(
                new SeatRequest(2, 1, AudienceType.ADULT))).id();
        em.flush();
        em.clear();
        statistics.clear();

        ReservationResponse response = reservationService.getById(reservationId, userId);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        // 응답이 쓰는 스칼라만 읽으므로 엔티티는 한 개도 올라오지 않는다.
        // 엔티티로 가져오면 branch.description(TEXT)처럼 안 쓰는 컴럼까지 따라온다.
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(response.seats()).hasSize(1);
        assertThat(response.screening().branchName()).isEqualTo("강남");
    }

    private ReservationCreateRequest requestOf(SeatRequest... seats) {
        return new ReservationCreateRequest(screeningId, List.of(seats));
    }
}
