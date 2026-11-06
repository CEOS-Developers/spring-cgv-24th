package com.ceos24.cgv.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceos24.cgv.domain.member.Role;
import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ScreeningRepository screeningRepository;

    @Autowired
    private ScreenRepository screenRepository;

    @Autowired
    private TheaterRepository theaterRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;
    private Long screeningId;
    private final Long seatNumber = 1L;

    @BeforeEach
    void setUp() {
        tearDown();

        Member member = memberRepository.save(Member.builder()
                .name("예매테스터")
                .email("reservation@cgv.com")
                .loginId("reservation_user")
                .password("password1234!")
                .role(Role.USER)
                .build());
        memberId = member.getId();

        Theater theater = theaterRepository.save(new Theater("CGV 용산", "서울시 용산구"));
        Screen screen = screenRepository.save(new Screen(theater, ScreenType.NORMAL, "1관", 50L));
        Movie movie = movieRepository.save(new Movie("인터스텔라"));
        Screening screening = screeningRepository.save(new Screening(
                movie,
                screen,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3)));
        screeningId = screening.getId();

        seatRepository.save(Seat.create(screening, seatNumber));
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        screeningRepository.deleteAll();
        screenRepository.deleteAll();
        theaterRepository.deleteAll();
        movieRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("[비관적 락 적용] 동일한 좌석에 50명이 동시에 예매 요청 시 1명만 성공하고 49명은 실패한다")
    void reserveSeat_ConcurrencyTest() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    reservationService.reserveSeat(memberId, screeningId, seatNumber);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
        assertThat(reservationRepository.count()).isEqualTo(1L);
    }
}
