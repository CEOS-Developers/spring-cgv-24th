package com.ceos24.spring_cgv.domain.payment.service;

import com.ceos24.spring_cgv.domain.member.entity.Member;
import com.ceos24.spring_cgv.domain.member.enums.Role;
import com.ceos24.spring_cgv.domain.member.repository.MemberRepository;
import com.ceos24.spring_cgv.domain.movie.entity.Cinema;
import com.ceos24.spring_cgv.domain.movie.entity.Movie;
import com.ceos24.spring_cgv.domain.movie.entity.Screen;
import com.ceos24.spring_cgv.domain.movie.entity.ScreenType;
import com.ceos24.spring_cgv.domain.movie.entity.Screening;
import com.ceos24.spring_cgv.domain.movie.enums.ScreenCategory;
import com.ceos24.spring_cgv.domain.movie.repository.CinemaRepository;
import com.ceos24.spring_cgv.domain.movie.repository.MovieRepository;
import com.ceos24.spring_cgv.domain.movie.repository.ScreenRepository;
import com.ceos24.spring_cgv.domain.movie.repository.ScreenTypeRepository;
import com.ceos24.spring_cgv.domain.movie.repository.ScreeningRepository;
import com.ceos24.spring_cgv.domain.payment.dto.request.PaymentReadyRequest;
import com.ceos24.spring_cgv.domain.payment.exception.PaymentException;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentSeatRepository;
import com.ceos24.spring_cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.spring_cgv.domain.reservation.enums.AgeGroup;
import com.ceos24.spring_cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.spring_cgv.domain.reservation.repository.ReservationSeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("좌석 예매(ready) 동시성 테스트")
class PaymentConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:9.7.0");

    @Autowired private PaymentService paymentService;

    @Autowired private MemberRepository memberRepository;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private ScreenTypeRepository screenTypeRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ScreeningRepository screeningRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationSeatRepository reservationSeatRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentSeatRepository paymentSeatRepository;

    // 같은 좌석을 노리는 사람 수
    private static final int REQUEST_COUNT = 32;

    // 모두가 노리는 단 하나의 좌석
    private static final int SEAT_ROW = 5;
    private static final int SEAT_NUMBER = 7;

    private Long screeningId;
    private List<Long> memberIds;

    @BeforeEach
    void setUp() {

        Cinema cinema = cinemaRepository.save(Cinema.builder()
                .name("CGV 강남")
                .region("강남")
                .address("강남대로 1")
                .build());

        // 10 x 10 좌석이므로 5행 7번은 유효한 좌석이다.
        ScreenType screenType = screenTypeRepository.save(ScreenType.builder()
                .name("2D")
                .category(ScreenCategory.GENERAL)
                .rowCount(10)
                .colCount(10)
                .extraCharge(2000)
                .build());

        Screen screen = screenRepository.save(Screen.builder()
                .cinema(cinema)
                .screenType(screenType)
                .name("1관")
                .build());

        Movie movie = movieRepository.save(Movie.builder()
                .title("오디세우스")
                .price(10000)
                .runningTime(120)
                .build());

        // 아직 시작하지 않은 회차여야 예매할 수 있다.
        Screening screening = screeningRepository.save(Screening.builder()
                .movie(movie)
                .screen(screen)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusMinutes(120))
                .basePrice(10000)
                .build());

        screeningId = screening.getId();

        // 요청자마다 다른 회원이어야 실제 상황에 가깝다.
        memberIds = new ArrayList<>();
        for (int i = 0; i < REQUEST_COUNT; i++) {
            Member member = memberRepository.save(Member.builder()
                    .name("회원" + i)
                    .email("concurrency" + i + "@test.com")
                    .password("password")
                    .role(Role.USER)
                    .build());
            memberIds.add(member.getId());
        }
    }

    @AfterEach
    void tearDown() {
        // 외래 키 때문에 자식부터 지운다.
        paymentSeatRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        reservationSeatRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        screeningRepository.deleteAllInBatch();
        screenRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
        screenTypeRepository.deleteAllInBatch();
        cinemaRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("동시에 예매하면 여러 명이 같은 좌석을 점유한다.")
    void 여러회원이_같은좌석을_동시에_예매() throws InterruptedException {

        int threadCount = REQUEST_COUNT;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGun = new CountDownLatch(1);      // 동시에 출발시키기 위한 신호탄
        CountDownLatch finished = new CountDownLatch(threadCount);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        Queue<String> unexpected = new ConcurrentLinkedQueue<>();

        for (Long memberId : memberIds) {
            executor.submit(() -> {
                try {
                    startGun.await(); // 모든 스레드가 여기서 대기한다.
                    paymentService.ready(memberId, sameSeatRequest());
                    success.incrementAndGet();

                } catch (PaymentException e) {
                    rejected.incrementAndGet();

                } catch (Exception e) {
                    // 예외를 삼키면 예상치 못한 실패가 성공으로 보여 결과를 신뢰할 수 없다.
                    failed.incrementAndGet();
                    unexpected.add(e.getClass().getSimpleName() + ": " + e.getMessage());

                } finally {
                    finished.countDown();
                }
            });
        }

        startGun.countDown(); // 32개 요청이 동시에 출발한다.
        finished.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long reservedCount = countReservedRowsOfTargetSeat();
        printResult("동시 요청", success.get(), rejected.get(), failed.get(), reservedCount);
        unexpected.forEach(message -> System.out.println("  예상치 못한 예외 > " + message));

        // 좌석 하나에 RESERVED 행이 2개 이상 생겼다면 동시성 문제가 재현된 것이다.
        // 해결편에서 락이나 유니크 제약을 적용한 뒤에는 이 단정을 isEqualTo(1)로 바꾸면 회귀 테스트가 된다.
        assertThat(reservedCount).isGreaterThan(1);
    }

    /***
     * 함수 기능: 모든 요청이 노리는 같은 좌석 하나로 결제 요청을 만든다.
     * @return 좌석 1석(5행 7번, 성인)의 결제 요청
     */
    private PaymentReadyRequest sameSeatRequest() {

        return new PaymentReadyRequest(
                screeningId,
                List.of(new PaymentReadyRequest.SeatRequest(SEAT_ROW, SEAT_NUMBER, AgeGroup.ADULT)));
    }

    /***
     * 함수 기능: 목표 좌석을 현재 점유(RESERVED)하고 있는 행이 몇 개인지 센다.
     *          정상이라면 1이고, 2 이상이면 여러 사람이 같은 좌석을 가져간 것이다.
     * @return 목표 좌석의 RESERVED 행 수
     */
    private long countReservedRowsOfTargetSeat() {

        return reservationSeatRepository.findAllByScreeningId(screeningId).stream()
                .filter(seat -> seat.getSeatRow() == SEAT_ROW && seat.getSeatNumber() == SEAT_NUMBER)
                .filter(ReservationSeat::isReserved)
                .count();
    }

    private void printResult(String label, int success, int rejected, int failed, long reservedCount) {

        System.out.printf("""

                        ========== %s 결과 ==========
                        요청 수              : %d
                        ready 성공           : %d
                        좌석 중복으로 거절   : %d
                        그 외 실패           : %d
                        ------------------------------------
                        %d행 %d번 좌석의 RESERVED 행 수 : %d   (정상이라면 1)
                        ====================================
                        %n""",
                label, REQUEST_COUNT, success, rejected, failed,
                SEAT_ROW, SEAT_NUMBER, reservedCount);
    }
}
