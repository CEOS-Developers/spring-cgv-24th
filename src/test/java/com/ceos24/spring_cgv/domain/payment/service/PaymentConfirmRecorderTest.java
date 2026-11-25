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
import com.ceos24.spring_cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentConfirmResponse;
import com.ceos24.spring_cgv.domain.payment.entity.Payment;
import com.ceos24.spring_cgv.domain.payment.entity.PaymentConfirm;
import com.ceos24.spring_cgv.domain.payment.entity.PaymentSeat;
import com.ceos24.spring_cgv.domain.payment.enums.ConfirmStatus;
import com.ceos24.spring_cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.spring_cgv.domain.payment.exception.PaymentException;
import com.ceos24.spring_cgv.domain.payment.exception.code.PaymentErrorCode;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentConfirmRepository;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentSeatRepository;
import com.ceos24.spring_cgv.domain.payment.repository.SeatHoldRepository;
import com.ceos24.spring_cgv.domain.reservation.enums.AgeGroup;
import com.ceos24.spring_cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.spring_cgv.domain.reservation.repository.ReservationSeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/***
 * 커밋/롤백 결과를 실제 DB로 확인해야 하므로 테스트에 @Transactional을 붙이지 않는다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("결제 승인 시도 기록(REQUIRES_NEW) 통합 테스트")
class PaymentConfirmRecorderTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:9.7.0");

    @MockitoBean private TossPaymentClient tossPaymentClient;

    // Redis 없이 DB 트랜잭션만 검증한다.
    @MockitoBean private SeatHoldRepository seatHoldRepository;
    @MockitoBean private RedissonClient redissonClient;

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
    @Autowired private PaymentConfirmRepository paymentConfirmRepository;

    private static final String ORDER_ID = "cgv_confirm1";
    private static final String PAYMENT_KEY = "tgen_confirm_test";
    private static final int AMOUNT = 12000;

    @BeforeEach
    void setUp() {

        Cinema cinema = cinemaRepository.save(Cinema.builder()
                .name("CGV 강남").region("강남").address("강남대로 1").build());
        ScreenType screenType = screenTypeRepository.save(ScreenType.builder()
                .name("2D").category(ScreenCategory.GENERAL)
                .rowCount(10).colCount(10).extraCharge(2000).build());
        Screen screen = screenRepository.save(Screen.builder()
                .cinema(cinema).screenType(screenType).name("1관").build());
        Movie movie = movieRepository.save(Movie.builder()
                .title("오디세우스").price(10000).runningTime(120).build());
        Screening screening = screeningRepository.save(Screening.builder()
                .movie(movie).screen(screen)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusMinutes(120))
                .basePrice(10000).build());
        Member member = memberRepository.save(Member.builder()
                .name("회원").email("confirm@test.com").password("password").role(Role.USER).build());
        Payment payment = paymentRepository.save(Payment.builder()
                .member(member).screening(screening)
                .orderId(ORDER_ID).orderName("오디세우스 1석").totalAmount(AMOUNT).build());
        paymentSeatRepository.save(PaymentSeat.builder()
                .payment(payment).seatRow(5).seatNumber(7)
                .ageGroup(AgeGroup.ADULT).price(AMOUNT).build());
    }

    @AfterEach
    void tearDown() {
        // 외래 키 때문에 자식부터 지운다.
        paymentConfirmRepository.deleteAllInBatch();
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
    @DisplayName("승인에 성공하면 결제는 DONE, 시도는 APPROVED가 된다.")
    void 승인_성공() {

        given(tossPaymentClient.confirm(any(), any(), any())).willReturn(response("카드"));

        paymentService.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT);

        assertThat(findPayment().getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(findAttempt().getStatus()).isEqualTo(ConfirmStatus.APPROVED);
    }

    @Test
    @DisplayName("승인 뒤 로컬 처리가 실패하면 예매는 롤백되지만 승인 기록은 남는다.")
    void 승인_후_로컬_실패() {

        // 지원하지 않는 결제 수단이면 토스 승인 이후에 예외가 난다.
        given(tossPaymentClient.confirm(any(), any(), any())).willReturn(response("미지원수단"));

        assertFailsWith(PaymentErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);

        assertRolledBack();
        PaymentConfirm attempt = findAttempt();
        assertThat(attempt.getStatus()).isEqualTo(ConfirmStatus.APPROVED);
        assertThat(attempt.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(attempt.getTransactionKey()).isEqualTo("txn_confirm");
    }

    @Test
    @DisplayName("승인 결과를 모르면 예매는 롤백되지만 시도는 REQUESTED로 남는다.")
    void 승인_결과_불명() {

        given(tossPaymentClient.confirm(any(), any(), any()))
                .willThrow(new PaymentException(PaymentErrorCode.PAYMENT_CONFIRM_RESULT_UNKNOWN));

        assertFailsWith(PaymentErrorCode.PAYMENT_CONFIRM_RESULT_UNKNOWN);

        assertRolledBack();
        PaymentConfirm attempt = findAttempt();
        assertThat(attempt.getStatus()).isEqualTo(ConfirmStatus.REQUESTED);
        assertThat(attempt.getPaymentKey()).isEqualTo(PAYMENT_KEY);
    }

    @Test
    @DisplayName("토스가 거절하면 예매는 롤백되지만 시도는 FAILED로 남는다.")
    void 승인_거절() {

        given(tossPaymentClient.confirm(any(), any(), any()))
                .willThrow(new PaymentException(PaymentErrorCode.PAYMENT_CONFIRM_FAILED));

        assertFailsWith(PaymentErrorCode.PAYMENT_CONFIRM_FAILED);

        assertRolledBack();
        assertThat(findAttempt().getStatus()).isEqualTo(ConfirmStatus.FAILED);
    }

    private void assertFailsWith(PaymentErrorCode errorCode) {
        assertThatThrownBy(() -> paymentService.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT))
                .isInstanceOfSatisfying(PaymentException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    private void assertRolledBack() {
        assertThat(findPayment().getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(findPayment().getPaymentKey()).isNull();
        assertThat(reservationRepository.count()).isZero();
        assertThat(reservationSeatRepository.count()).isZero();
    }

    private Payment findPayment() {
        return paymentRepository.findByOrderId(ORDER_ID).orElseThrow();
    }

    private PaymentConfirm findAttempt() {
        assertThat(paymentConfirmRepository.count()).isEqualTo(1);
        return paymentConfirmRepository.findAll().getFirst();
    }

    private TossPaymentConfirmResponse response(String method) {
        return new TossPaymentConfirmResponse(PAYMENT_KEY, ORDER_ID, "오디세우스 1석", "DONE",
                AMOUNT, method, OffsetDateTime.now(), OffsetDateTime.now(), "txn_confirm");
    }
}
