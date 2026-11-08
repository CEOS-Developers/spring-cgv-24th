package com.ceos24.spring_cgv.domain.payment.service;

import com.ceos24.spring_cgv.domain.member.repository.MemberRepository;
import com.ceos24.spring_cgv.domain.movie.entity.Screening;
import com.ceos24.spring_cgv.domain.movie.repository.ScreeningRepository;
import com.ceos24.spring_cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentCancelResponse;
import com.ceos24.spring_cgv.domain.payment.dto.CancelAttempt;
import com.ceos24.spring_cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.spring_cgv.domain.payment.dto.response.PaymentCancelResponse;
import com.ceos24.spring_cgv.domain.payment.entity.Payment;
import com.ceos24.spring_cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.spring_cgv.domain.payment.entity.PaymentSeat;
import com.ceos24.spring_cgv.domain.payment.enums.CancelStatus;
import com.ceos24.spring_cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.spring_cgv.domain.payment.enums.TossPaymentMethod;
import com.ceos24.spring_cgv.domain.payment.exception.PaymentException;
import com.ceos24.spring_cgv.domain.payment.exception.code.PaymentErrorCode;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentCancelRepository;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentSeatRepository;
import com.ceos24.spring_cgv.domain.payment.repository.SeatHoldRepository;
import com.ceos24.spring_cgv.domain.reservation.entity.Reservation;
import com.ceos24.spring_cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.spring_cgv.domain.reservation.enums.AgeGroup;
import com.ceos24.spring_cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.spring_cgv.domain.reservation.service.ReservationService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 결제 취소 단위 테스트")
class PaymentCancelServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentSeatRepository paymentSeatRepository;

    @Mock
    private PaymentCancelRepository paymentCancelRepository;

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ScreeningRepository screeningRepository;

    @Mock
    private ReservationSeatRepository reservationSeatRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private ReservationService reservationService;

    @Mock
    private PaymentCancelRecorder paymentCancelRecorder;

    @InjectMocks
    private PaymentService paymentService;

    private static final Long MEMBER_ID = 7L;
    private static final Long PAYMENT_ID = 1L;
    private static final Long RESERVATION_ID = 11L;
    private static final Long SCREENING_ID = 12L;
    private static final Long CANCEL_ID = 100L;
    private static final String ORDER_ID = "cgv_8f14e45f";
    private static final String PAYMENT_KEY = "tgen_20260116142345abcdef";
    private static final String REASON = "고객 요청";
    private static final int SEAT_PRICE = 14000;
    private static final int TOTAL_AMOUNT = SEAT_PRICE * 2;

    // 취소 지문 — 좌석 집합으로 "어떤 취소인지"를 식별한다. 첫 시도의 멱등성 키는 지문과 같다.
    private static final String FP_FULL = "cancel-" + ORDER_ID + "-5:7,5:8";
    private static final String FP_SEAT_7 = "cancel-" + ORDER_ID + "-5:7";
    private static final String FP_SEAT_8 = "cancel-" + ORDER_ID + "-5:8";

    // 취소가 허용하는 상태 집합. 전액 취소된 결제는 메시지를 구분하기 위해 함께 조회한다.
    private static final List<PaymentStatus> CANCELABLE =
            List.of(PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED, PaymentStatus.CANCELED);

    private Screening screening;
    private Reservation reservation;
    private Payment payment;
    private List<PaymentSeat> paymentSeats;

    @BeforeEach
    void setUp() {

        // 상영 시작 전이어야 취소할 수 있다.
        screening = Screening.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .basePrice(12000)
                .build();
        ReflectionTestUtils.setField(screening, "id", SCREENING_ID);

        reservation = Reservation.builder()
                .screening(screening)
                .price(TOTAL_AMOUNT)
                .build();
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);

        payment = Payment.builder()
                .screening(screening)
                .orderId(ORDER_ID)
                .orderName("아바타: 물의 길 2석")
                .totalAmount(TOTAL_AMOUNT)
                .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);

        payment.linkReservation(reservation);
        payment.confirm(PAYMENT_KEY, TossPaymentMethod.CARD,
                LocalDateTime.now().minusHours(1), "txn_abcdef");

        paymentSeats = List.of(paymentSeat(5, 7), paymentSeat(5, 8));
    }

    private PaymentSeat paymentSeat(int row, int number) {

        return PaymentSeat.builder()
                .payment(payment)
                .seatRow(row)
                .seatNumber(number)
                .ageGroup(AgeGroup.ADULT)
                .price(SEAT_PRICE)
                .build();
    }

    private ReservationSeat reservationSeat(int row, int number) {

        return ReservationSeat.builder()
                .reservation(reservation)
                .screening(screening)
                .seatRow(row)
                .seatNumber(number)
                .ageGroup(AgeGroup.ADULT)
                .price(SEAT_PRICE)
                .build();
    }

    private PaymentCancel paymentCancel(int cancelAmount, String fingerprint) {

        PaymentCancel cancel = PaymentCancel.builder()
                .payment(payment)
                .cancelAmount(cancelAmount)
                .reason(REASON)
                .idempotencyKey(fingerprint)
                .requestFingerprint(fingerprint)
                .build();

        ReflectionTestUtils.setField(cancel, "id", CANCEL_ID);
        return cancel;
    }

    /***
     * 함수 기능: 토스 취소 응답을 만든다.
     * @param cancelAmount 이번에 취소한 금액
     * @param balanceAmount 취소 후 남은 금액
     * @return 토스 취소 응답
     */
    private TossPaymentCancelResponse tossResponse(int cancelAmount, int balanceAmount) {

        return new TossPaymentCancelResponse(
                PAYMENT_KEY,
                ORDER_ID,
                balanceAmount == 0 ? "CANCELED" : "PARTIAL_CANCELED",
                TOTAL_AMOUNT,
                balanceAmount,
                List.of(new TossPaymentCancelResponse.CancelDetail(
                        "txn_cancel_1",
                        cancelAmount,
                        OffsetDateTime.of(2026, 10, 16, 15, 10, 0, 0, ZoneOffset.ofHours(9)),
                        REASON)));
    }

    private PaymentCancelRequest fullCancelRequest() {
        return new PaymentCancelRequest(null, REASON);
    }

    private PaymentCancelRequest seatCancelRequest(int row, int number) {
        return new PaymentCancelRequest(
                List.of(new PaymentCancelRequest.SeatRequest(row, number)), REASON);
    }

    private void givenPaymentFound() {
        given(paymentRepository.findByIdAndMemberIdAndStatusIn(PAYMENT_ID, MEMBER_ID, CANCELABLE))
                .willReturn(Optional.of(payment));
    }

    private void givenNoPendingCancel() {
        given(paymentCancelRepository.findFirstByPaymentIdAndStatus(PAYMENT_ID, CancelStatus.REQUESTED))
                .willReturn(Optional.empty());
    }

    private void givenSeatsFound() {
        given(paymentSeatRepository.findAllByPaymentId(PAYMENT_ID)).willReturn(paymentSeats);
    }

    @Nested
    @DisplayName("전체 취소")
    class 전체_취소 {

        @Test
        @DisplayName("좌석을 지정하지 않으면 남은 좌석 전체가 취소되고 결제가 CANCELED가 된다.")
        void 전체_취소_성공() {

            // given
            givenPaymentFound();
            givenNoPendingCancel();
            givenSeatsFound();
            given(paymentCancelRecorder.record(PAYMENT_ID, TOTAL_AMOUNT, REASON, FP_FULL))
                    .willReturn(new CancelAttempt(CANCEL_ID, FP_FULL));
            given(tossPaymentClient.cancel(PAYMENT_KEY, TOTAL_AMOUNT, REASON, FP_FULL))
                    .willReturn(tossResponse(TOTAL_AMOUNT, 0));
            given(paymentCancelRepository.findWithLockById(CANCEL_ID))
                    .willReturn(Optional.of(paymentCancel(TOTAL_AMOUNT, FP_FULL)));
            given(reservationSeatRepository.findAllByReservationId(RESERVATION_ID))
                    .willReturn(List.of(reservationSeat(5, 7), reservationSeat(5, 8)));

            // when
            PaymentCancelResponse response = paymentService.cancel(MEMBER_ID, PAYMENT_ID, fullCancelRequest());

            // then
            assertThat(response.status()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(response.canceledAmountThisTime()).isEqualTo(TOTAL_AMOUNT);
            assertThat(response.canceledAmount()).isEqualTo(TOTAL_AMOUNT);
            assertThat(response.balanceAmount()).isZero();
            assertThat(response.canceledSeats()).hasSize(2);
            assertThat(payment.getCanceledAt()).isNotNull();
        }

        @Test
        @DisplayName("취소한 좌석만 예매 좌석 점유 해제로 넘어간다.")
        void 전체_취소시_좌석_해제_위임() {

            // given
            givenPaymentFound();
            givenNoPendingCancel();
            givenSeatsFound();
            given(paymentCancelRecorder.record(PAYMENT_ID, TOTAL_AMOUNT, REASON, FP_FULL))
                    .willReturn(new CancelAttempt(CANCEL_ID, FP_FULL));
            given(tossPaymentClient.cancel(PAYMENT_KEY, TOTAL_AMOUNT, REASON, FP_FULL))
                    .willReturn(tossResponse(TOTAL_AMOUNT, 0));
            given(paymentCancelRepository.findWithLockById(CANCEL_ID))
                    .willReturn(Optional.of(paymentCancel(TOTAL_AMOUNT, FP_FULL)));
            given(reservationSeatRepository.findAllByReservationId(RESERVATION_ID))
                    .willReturn(List.of(reservationSeat(5, 7), reservationSeat(5, 8)));

            // when
            paymentService.cancel(MEMBER_ID, PAYMENT_ID, fullCancelRequest());

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ReservationSeat>> captor = ArgumentCaptor.forClass(List.class);
            verify(reservationService).unreserveSeats(any(Reservation.class), captor.capture());
            assertThat(captor.getValue()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("부분 취소")
    class 부분_취소 {

        @Test
        @DisplayName("2석 중 1석만 취소하면 PARTIAL_CANCELED가 되고 남은 금액이 1석 금액이다.")
        void 부분_취소_성공() {

            // given
            givenPaymentFound();
            givenNoPendingCancel();
            givenSeatsFound();
            given(paymentCancelRecorder.record(PAYMENT_ID, SEAT_PRICE, REASON, FP_SEAT_7))
                    .willReturn(new CancelAttempt(CANCEL_ID, FP_SEAT_7));
            given(tossPaymentClient.cancel(PAYMENT_KEY, SEAT_PRICE, REASON, FP_SEAT_7))
                    .willReturn(tossResponse(SEAT_PRICE, SEAT_PRICE));
            given(paymentCancelRepository.findWithLockById(CANCEL_ID))
                    .willReturn(Optional.of(paymentCancel(SEAT_PRICE, FP_SEAT_7)));
            given(reservationSeatRepository.findAllByReservationId(RESERVATION_ID))
                    .willReturn(List.of(reservationSeat(5, 7), reservationSeat(5, 8)));

            // when
            PaymentCancelResponse response = paymentService.cancel(MEMBER_ID, PAYMENT_ID, seatCancelRequest(5, 7));

            // then
            assertThat(response.status()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
            assertThat(response.canceledAmountThisTime()).isEqualTo(SEAT_PRICE);
            assertThat(response.canceledAmount()).isEqualTo(SEAT_PRICE);
            assertThat(response.balanceAmount()).isEqualTo(SEAT_PRICE);
            assertThat(response.canceledSeats()).hasSize(1);
            assertThat(response.canceledSeats().getFirst().seatNumber()).isEqualTo(7);
            // 전액 취소가 아니므로 취소 시각은 아직 비어 있다.
            assertThat(payment.getCanceledAt()).isNull();
        }

        @Test
        @DisplayName("부분 취소를 두 번 해서 전액에 도달하면 CANCELED가 되고 남은 금액이 0이 된다.")
        void 부분_취소_두번_누적() {

            // given
            givenPaymentFound();
            givenSeatsFound();
            givenNoPendingCancel();
            given(reservationSeatRepository.findAllByReservationId(RESERVATION_ID))
                    .willReturn(List.of(reservationSeat(5, 7), reservationSeat(5, 8)));
            // 좌석이 다르므로 지문도 달라 서로 다른 멱등성 키를 쓴다. 토스는 별개의 취소로 처리한다.
            given(paymentCancelRecorder.record(PAYMENT_ID, SEAT_PRICE, REASON, FP_SEAT_7))
                    .willReturn(new CancelAttempt(CANCEL_ID, FP_SEAT_7));
            given(paymentCancelRecorder.record(PAYMENT_ID, SEAT_PRICE, REASON, FP_SEAT_8))
                    .willReturn(new CancelAttempt(CANCEL_ID, FP_SEAT_8));
            given(paymentCancelRepository.findWithLockById(CANCEL_ID))
                    .willReturn(Optional.of(paymentCancel(SEAT_PRICE, FP_SEAT_7)))
                    .willReturn(Optional.of(paymentCancel(SEAT_PRICE, FP_SEAT_8)));
            // 1차는 잔액 14000, 2차는 잔액 0을 돌려준다.
            given(tossPaymentClient.cancel(PAYMENT_KEY, SEAT_PRICE, REASON, FP_SEAT_7))
                    .willReturn(tossResponse(SEAT_PRICE, SEAT_PRICE));
            given(tossPaymentClient.cancel(PAYMENT_KEY, SEAT_PRICE, REASON, FP_SEAT_8))
                    .willReturn(tossResponse(SEAT_PRICE, 0));

            // when
            paymentService.cancel(MEMBER_ID, PAYMENT_ID, seatCancelRequest(5, 7));
            PaymentCancelResponse second = paymentService.cancel(MEMBER_ID, PAYMENT_ID, seatCancelRequest(5, 8));

            // then
            assertThat(second.status()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(second.canceledAmount()).isEqualTo(TOTAL_AMOUNT);
            assertThat(second.balanceAmount()).isZero();
        }
    }

    @Nested
    @DisplayName("취소 실패")
    class 취소_실패 {

        @Test
        @DisplayName("이미 전액 취소된 결제를 다시 취소하면 실패한다.")
        void 전액_취소된_결제_재취소_실패() {

            // given
            payment.applyCancel(0, LocalDateTime.now());
            givenPaymentFound();

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.cancel(MEMBER_ID, PAYMENT_ID, fullCancelRequest()))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_CANCELED);
        }

        @Test
        @DisplayName("이미 취소된 좌석을 다시 취소하면 실패한다.")
        void 이미_취소된_좌석_재취소_실패() {

            // given
            givenPaymentFound();
            givenSeatsFound();
            paymentSeats.getFirst().markCanceled(paymentCancel(SEAT_PRICE, FP_SEAT_7));

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.cancel(MEMBER_ID, PAYMENT_ID, seatCancelRequest(5, 7)))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_SEAT_ALREADY_CANCELED);
        }

        @Test
        @DisplayName("남은 금액보다 많은 금액을 취소하려 하면 실패한다.")
        void 남은_금액_초과_실패() {

            // given
            // 좌석 2석(28000)을 취소하려 하지만 이미 14000이 취소되어 잔액은 14000뿐이다.
            ReflectionTestUtils.setField(payment, "canceledAmount", SEAT_PRICE);
            givenPaymentFound();
            givenSeatsFound();

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.cancel(MEMBER_ID, PAYMENT_ID, fullCancelRequest()))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED);
        }

        @Test
        @DisplayName("이 결제에 없는 좌석을 취소하려 하면 실패한다.")
        void 존재하지_않는_좌석_실패() {

            // given
            givenPaymentFound();
            givenSeatsFound();

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.cancel(MEMBER_ID, PAYMENT_ID, seatCancelRequest(9, 9)))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CANCEL_SEAT_NOT_FOUND);
        }

        @Test
        @DisplayName("상영이 시작된 회차의 결제는 취소할 수 없다.")
        void 상영_시작후_취소_실패() {

            // given
            ReflectionTestUtils.setField(screening, "startTime", LocalDateTime.now().minusMinutes(1));
            givenPaymentFound();

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.cancel(MEMBER_ID, PAYMENT_ID, fullCancelRequest()))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_CANCELABLE);
        }

        @Test
        @DisplayName("남의 결제는 조회되지 않아 취소할 수 없다.")
        void 남의_결제_취소_실패() {

            // given
            given(paymentRepository.findByIdAndMemberIdAndStatusIn(PAYMENT_ID, 999L, CANCELABLE))
                    .willReturn(Optional.empty());

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.cancel(999L, PAYMENT_ID, fullCancelRequest()))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PG 호출 실패")
    class PG_호출_실패 {

        @Test
        @DisplayName("토스가 명시적으로 거절하면 취소 시도를 실패로 확정하고 결제 상태는 그대로 둔다.")
        void 토스_거절시_실패_확정() {

            // given
            givenPaymentFound();
            givenNoPendingCancel();
            givenSeatsFound();
            given(paymentCancelRecorder.record(PAYMENT_ID, TOTAL_AMOUNT, REASON, FP_FULL))
                    .willReturn(new CancelAttempt(CANCEL_ID, FP_FULL));
            given(tossPaymentClient.cancel(PAYMENT_KEY, TOTAL_AMOUNT, REASON, FP_FULL))
                    .willThrow(new PaymentException(PaymentErrorCode.PAYMENT_CANCEL_FAILED));

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.cancel(MEMBER_ID, PAYMENT_ID, fullCancelRequest()))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CANCEL_FAILED);

            verify(paymentCancelRecorder).markFailed(CANCEL_ID);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
            assertThat(payment.getCanceledAmount()).isZero();
        }

        @Test
        @DisplayName("응답을 받지 못하면 취소 시도를 REQUESTED로 남겨 두고 실패로 확정하지 않는다.")
        void 응답_없음시_미결_유지() {

            // given
            givenPaymentFound();
            givenNoPendingCancel();
            givenSeatsFound();
            given(paymentCancelRecorder.record(PAYMENT_ID, TOTAL_AMOUNT, REASON, FP_FULL))
                    .willReturn(new CancelAttempt(CANCEL_ID, FP_FULL));
            given(tossPaymentClient.cancel(PAYMENT_KEY, TOTAL_AMOUNT, REASON, FP_FULL))
                    .willThrow(new PaymentException(PaymentErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN));

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.cancel(MEMBER_ID, PAYMENT_ID, fullCancelRequest()))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);

            // 취소됐는지 모르는 상태이므로 실패로 단정해서는 안 된다.
            verify(paymentCancelRecorder, never()).markFailed(anyLong());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        }

        @Test
        @DisplayName("결과를 확인하지 못한 취소가 있으면 다른 취소 요청을 거부한다.")
        void 다른_미결_취소_존재시_거부() {

            // given
            // 5행 7번 취소가 미결인 상태에서 2석 전체 취소를 요청한다. 지문이 다르므로 막아야 한다.
            givenPaymentFound();
            givenSeatsFound();
            given(paymentCancelRepository.findFirstByPaymentIdAndStatus(PAYMENT_ID, CancelStatus.REQUESTED))
                    .willReturn(Optional.of(paymentCancel(SEAT_PRICE, FP_SEAT_7)));

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.cancel(MEMBER_ID, PAYMENT_ID, fullCancelRequest()))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CANCEL_IN_PROGRESS);

            // 이중 환불을 막기 위해 토스를 호출하지 않아야 한다.
            verify(tossPaymentClient, never()).cancel(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("같은 취소를 재시도하면 막지 않고 같은 멱등성 키로 토스를 호출한다.")
        void 같은_취소_재시도시_같은_키_사용() {

            // given
            // 5행 7번 취소가 미결인 상태에서 같은 좌석을 다시 취소 요청한다. 지문이 같으므로 통과해야 한다.
            givenPaymentFound();
            givenSeatsFound();
            given(paymentCancelRepository.findFirstByPaymentIdAndStatus(PAYMENT_ID, CancelStatus.REQUESTED))
                    .willReturn(Optional.of(paymentCancel(SEAT_PRICE, FP_SEAT_7)));
            // 레코더가 기존 시도의 키를 그대로 돌려준다.
            given(paymentCancelRecorder.record(PAYMENT_ID, SEAT_PRICE, REASON, FP_SEAT_7))
                    .willReturn(new CancelAttempt(CANCEL_ID, FP_SEAT_7));
            given(tossPaymentClient.cancel(PAYMENT_KEY, SEAT_PRICE, REASON, FP_SEAT_7))
                    .willReturn(tossResponse(SEAT_PRICE, SEAT_PRICE));
            given(paymentCancelRepository.findWithLockById(CANCEL_ID))
                    .willReturn(Optional.of(paymentCancel(SEAT_PRICE, FP_SEAT_7)));
            given(reservationSeatRepository.findAllByReservationId(RESERVATION_ID))
                    .willReturn(List.of(reservationSeat(5, 7), reservationSeat(5, 8)));

            // when
            PaymentCancelResponse response = paymentService.cancel(MEMBER_ID, PAYMENT_ID, seatCancelRequest(5, 7));

            // then
            // 같은 키로 호출했으므로 토스가 원래 결과를 돌려주고 이중 환불이 일어나지 않는다.
            verify(tossPaymentClient).cancel(PAYMENT_KEY, SEAT_PRICE, REASON, FP_SEAT_7);
            assertThat(response.status()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
            assertThat(response.balanceAmount()).isEqualTo(SEAT_PRICE);
        }
    }
}
