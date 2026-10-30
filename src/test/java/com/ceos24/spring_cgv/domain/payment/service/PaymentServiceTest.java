package com.ceos24.spring_cgv.domain.payment.service;

import com.ceos24.spring_cgv.domain.member.repository.MemberRepository;
import com.ceos24.spring_cgv.domain.movie.repository.ScreeningRepository;
import com.ceos24.spring_cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.spring_cgv.domain.payment.dto.response.PaymentInfoResponse;
import com.ceos24.spring_cgv.domain.payment.entity.Payment;
import com.ceos24.spring_cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.spring_cgv.domain.payment.enums.TossPaymentMethod;
import com.ceos24.spring_cgv.domain.payment.exception.PaymentException;
import com.ceos24.spring_cgv.domain.payment.exception.code.PaymentErrorCode;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentSeatRepository;
import com.ceos24.spring_cgv.domain.payment.repository.SeatHoldRepository;
import com.ceos24.spring_cgv.domain.reservation.entity.Reservation;
import com.ceos24.spring_cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.spring_cgv.domain.reservation.service.ReservationService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentSeatRepository paymentSeatRepository;

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

    @InjectMocks
    private PaymentService paymentService;

    private static final Long MEMBER_ID = 7L;
    private static final Long PAYMENT_ID = 1L;
    private static final Long RESERVATION_ID = 11L;
    private static final String ORDER_ID = "cgv_8f14e45f";
    private static final String ORDER_NAME = "아바타: 물의 길 2석";
    private static final Integer TOTAL_AMOUNT = 28000;
    private static final String PAYMENT_KEY = "tgen_20260116142345abcdef";
    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 10, 16, 14, 23, 45);

    /***
     * 함수 기능: 승인(DONE)까지 진행된 결제를 만든다.
     *          member/screening은 조회 응답에 쓰이지 않으므로 채우지 않는다.
     * @return 예매가 연결되고 승인이 완료된 결제
     */
    private Payment createDonePayment() {

        Payment payment = Payment.builder()
                .orderId(ORDER_ID)
                .orderName(ORDER_NAME)
                .totalAmount(TOTAL_AMOUNT)
                .build();

        Reservation reservation = Reservation.builder()
                .price(TOTAL_AMOUNT)
                .build();

        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);

        payment.linkReservation(reservation);
        payment.confirm(PAYMENT_KEY, TossPaymentMethod.CARD, APPROVED_AT, "txn_abcdef");

        return payment;
    }

    @Nested
    @DisplayName("결제 단건 조회")
    class 결제_단건_조회 {

        @Test
        @DisplayName("승인 완료된 본인 결제를 조회하면 결제 정보를 반환한다.")
        void 결제_단건_조회_성공() {

            // given
            given(paymentRepository.findByIdAndMemberIdAndStatus(PAYMENT_ID, MEMBER_ID, PaymentStatus.DONE))
                    .willReturn(Optional.of(createDonePayment()));

            // when
            PaymentInfoResponse response = paymentService.findPayment(MEMBER_ID, PAYMENT_ID);

            // then
            assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(response.orderId()).isEqualTo(ORDER_ID);
            assertThat(response.orderName()).isEqualTo(ORDER_NAME);
            assertThat(response.amount()).isEqualTo(TOTAL_AMOUNT);
            assertThat(response.method()).isEqualTo(TossPaymentMethod.CARD.getDescription());
            assertThat(response.status()).isEqualTo(PaymentStatus.DONE);
            assertThat(response.paymentKey()).isEqualTo(PAYMENT_KEY);
            assertThat(response.requestedAt()).isNotNull();
            assertThat(response.approvedAt()).isEqualTo(APPROVED_AT);
            assertThat(response.reservationId()).isEqualTo(RESERVATION_ID);
        }

        @Test
        @DisplayName("존재하지 않거나 남의 결제이거나 승인되지 않은 결제를 조회하면 실패한다.")
        void 결제_단건_조회_실패() {

            // given
            // 회원과 DONE 조건이 쿼리에 함께 걸려 있으므로, 세 경우 모두 빈 결과로 모인다.
            given(paymentRepository.findByIdAndMemberIdAndStatus(999L, MEMBER_ID, PaymentStatus.DONE))
                    .willReturn(Optional.empty());

            // when & then
            Assertions.assertThatThrownBy(() ->
                            paymentService.findPayment(MEMBER_ID, 999L))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
    }
}
