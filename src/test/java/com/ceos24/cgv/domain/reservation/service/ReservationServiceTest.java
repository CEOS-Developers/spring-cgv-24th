package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentOrderResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository.SeatPositionProjection;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationDetailRow;
import com.ceos24.cgv.domain.reservation.dto.ReservationPaymentAttempt;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 9, 0);
    private static final LocalDateTime START = LocalDateTime.of(2024, 6, 1, 10, 0);

    @Mock ReservationRepository reservationRepository;
    @Mock ScreeningRepository screeningRepository;
    // 서비스는 Repository의 default 메서드(getByIdOrThrow)를 부른다. mock은 default 메서드까지 가짜로 만들므로
    // 실제 구현을 타게 해서 아래 findById stub이 그대로 쓰이게 한다.
    @Mock(answer = Answers.CALLS_REAL_METHODS) UserRepository userRepository;
    @Mock ReservationSeatRepository reservationSeatRepository;
    @Mock PaymentRepository paymentRepository;

    ReservationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new ReservationService(reservationRepository, screeningRepository,
                userRepository, reservationSeatRepository, paymentRepository, clock);
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    void 없는_회차면_SCREENING_NOT_FOUND() {
        given(screeningRepository.findByIdWithDetails(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, reqOf(1L, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCREENING_NOT_FOUND);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void 없는_사용자면_USER_NOT_FOUND() {
        given(screeningRepository.findByIdWithDetails(1L)).willReturn(Optional.of(screeningWith(1L, 14000, START)));
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, reqOf(1L, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 좌석이_범위를_벗어나면_SEAT_OUT_OF_RANGE() {
        givenScreeningAndUser(1L, 1L);

        // STANDARD rowCount=8 인 상영관에 row=9 요청
        assertThatThrownBy(() -> service.create(1L, reqOf(1L, new int[]{9, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SEAT_OUT_OF_RANGE);
        verify(reservationSeatRepository, never()).findOccupiedPositionsByScreeningId(any(), any(), any());
    }

    @Test
    void 요청_내_좌석이_중복이면_DUPLICATE_SEAT_IN_REQUEST() {
        givenScreeningAndUser(1L, 1L);

        assertThatThrownBy(() -> service.create(1L, reqOf(1L, new int[]{1, 1}, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
        verify(reservationSeatRepository, never()).findOccupiedPositionsByScreeningId(any(), any(), any());
    }

    @Test
    void 이미_점유된_좌석이면_SEAT_ALREADY_RESERVED_pre_check() {
        givenScreeningAndUser(1L, 1L);
        // 스텁 체인 안에서 다른 목을 스텁하면 Mockito가 미완성 스텁으로 본다
        List<SeatPositionProjection> occupied = List.of(positionOf(1, 1));
        given(reservationSeatRepository.findOccupiedPositionsByScreeningId(1L, ReservationStatus.PENDING, NOW))
                .willReturn(occupied);

        assertThatThrownBy(() -> service.create(1L, reqOf(1L, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_요청_유니크제약_충돌시_SEAT_ALREADY_RESERVED() {
        givenScreeningAndUser(1L, 1L);
        given(reservationRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uk_seat_screening_row_col_release"));

        assertThatThrownBy(() -> service.create(1L, reqOf(1L, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);
    }

    @Test
    void 좌석_선점은_PENDING으로_생성되고_만료시각이_붙는다() {
        givenScreeningAndUser(1L, 2L);
        givenSaveAssignsId(10L);

        // 정렬 검증을 위해 순서를 뒤섞어 요청 (B3 → A2 → A1)
        ReservationResponse response = service.create(2L,
                reqOf(1L, new int[]{2, 3}, new int[]{1, 2}, new int[]{1, 1}));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(response.confirmedAt()).isNull();
        assertThat(response.seats()).extracting(ReservationResponse.SeatSummary::label)
                .containsExactly("A1", "A2", "B3");
        assertThat(response.totalPrice()).isEqualTo(14000 * 3);
    }

    @Test
    void 권종별_할인가가_좌석에_스냅샷된다() {
        givenScreeningAndUser(1L, 1L);
        givenSaveAssignsId(1L);

        service.create(1L, new ReservationCreateRequest(1L, List.of(
                new ReservationCreateRequest.SeatRequest(1, 1, AudienceType.ADULT),
                new ReservationCreateRequest.SeatRequest(1, 2, AudienceType.YOUTH),
                new ReservationCreateRequest.SeatRequest(1, 3, AudienceType.SENIOR))));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSeats()).extracting(ReservationSeat::getPaidPrice)
                .containsExactly(14000, 11200, 7000);
        assertThat(captor.getValue().getTotalPrice()).isEqualTo(32200);
    }

    @Test
    void 만료된_선점은_좌석을_잡기_전에_정리된다() {
        givenScreeningAndUser(1L, 1L);
        givenSaveAssignsId(2L);
        given(reservationRepository.findExpiredHoldsBlocking(
                eq(1L), anyList(), eq(ReservationStatus.PENDING), eq(NOW)))
                .willReturn(List.of(7L));
        given(reservationRepository.expireIfPending(7L, ReservationStatus.PENDING, ReservationStatus.EXPIRED, NOW))
                .willReturn(1);

        service.create(1L, reqOf(1L, new int[]{1, 1}));

        // 해제가 새 좌석 INSERT보다 먼저 나가야 유니크 제약에 걸리지 않는다
        InOrder order = inOrder(reservationRepository, reservationSeatRepository);
        order.verify(reservationRepository).expireIfPending(7L, ReservationStatus.PENDING, ReservationStatus.EXPIRED, NOW);
        order.verify(reservationSeatRepository).releaseOccupied(7L, NOW);
        order.verify(reservationRepository).saveAndFlush(any());
    }

    // 다른 요청이 먼저 만료시킨 선점이다. 좌석까지 같은 값으로 다시 쓰면 이 트랜잭션의 스냅샷에 예매와 좌석이 엇갈려 보인다.
    @Test
    void 다른_요청이_먼저_만료시킨_선점의_좌석은_건드리지_않는다() {
        givenScreeningAndUser(1L, 1L);
        givenSaveAssignsId(2L);
        given(reservationRepository.findExpiredHoldsBlocking(
                eq(1L), anyList(), eq(ReservationStatus.PENDING), eq(NOW)))
                .willReturn(List.of(7L));
        given(reservationRepository.expireIfPending(7L, ReservationStatus.PENDING, ReservationStatus.EXPIRED, NOW))
                .willReturn(0);

        service.create(1L, reqOf(1L, new int[]{1, 1}));

        verify(reservationSeatRepository, never()).releaseOccupied(anyLong(), any());
        verify(reservationRepository).saveAndFlush(any());
    }

    @Test
    void 정리_대상은_요청한_좌석을_막고_있는_선점으로_좁힌다() {
        givenScreeningAndUser(1L, 1L);
        givenSaveAssignsId(2L);

        service.create(1L, reqOf(1L, new int[]{2, 3}, new int[]{1, 5}));

        // 회차의 만료 선점을 전부 풀면 그 행들에 UPDATE 락이 걸려, 같은 회차를 골랐을 뿐인
        // 다른 좌석 요청까지 서로를 기다린다. 요청한 좌석만 키로 넘긴다.
        ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).findExpiredHoldsBlocking(
                eq(1L), captor.capture(), eq(ReservationStatus.PENDING), eq(NOW));
        assertThat(captor.getValue()).containsExactlyInAnyOrder(203, 105);
    }

    @Test
    void 좌석은_행_열_순서로_INSERT된다() {
        givenScreeningAndUser(1L, 1L);
        givenSaveAssignsId(2L);

        service.create(1L, reqOf(1L, new int[]{2, 3}, new int[]{1, 5}, new int[]{2, 1}));

        // INSERT 순서가 곧 락 획득 순서다. 모든 요청이 같은 순서를 쓰면 데드락이 생길 수 없다.
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSeats()).extracting(ReservationSeat::getLabel)
                .containsExactly("A5", "B1", "B3");
    }

    @Test
    void 데드락이나_락_타임아웃은_SEAT_RESERVATION_CONFLICT로_바뀐다() {
        givenScreeningAndUser(1L, 1L);
        given(reservationRepository.saveAndFlush(any(Reservation.class)))
                .willThrow(new CannotAcquireLockException("lock wait timeout"));

        assertThatThrownBy(() -> service.create(1L, reqOf(1L, new int[]{1, 1})))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SEAT_RESERVATION_CONFLICT);
    }

    // ─── 결제 주문 생성 ───────────────────────────────────────────────────────

    @Test
    void 결제_주문은_좌석_가격_합을_금액으로_새_주문번호를_발급한다() {
        Reservation hold = holdWithId(1L, NOW, 1, 1, 1, 2);
        given(reservationRepository.findOwnedForUpdate(1L, 2L)).willReturn(Optional.of(hold));
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

        PaymentOrderResponse first = service.createPaymentOrder(1L, 2L);
        PaymentOrderResponse second = service.createPaymentOrder(1L, 2L);

        assertThat(first.amount()).isEqualTo(28000);
        assertThat(first.orderName()).isEqualTo("범죄도시4 2매");
        // 토스 규칙: 6~64자, 영문·숫자·-·_
        assertThat(first.orderId()).startsWith("cgv-r-").hasSize(38).matches("[A-Za-z0-9_-]+");
        assertThat(second.orderId()).isNotEqualTo(first.orderId());
    }

    @Test
    void 만료된_선점은_결제_주문을_만들_수_없다() {
        Reservation hold = holdWithId(1L, NOW.minusMinutes(11), 1, 1);
        given(reservationRepository.findOwnedForUpdate(1L, 2L)).willReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.createPaymentOrder(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_EXPIRED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 승인_중인_결제가_있으면_새_주문을_만들_수_없다() {
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        given(reservationRepository.findOwnedForUpdate(1L, 2L)).willReturn(Optional.of(hold));
        given(paymentRepository.existsByReservationIdAndStatus(1L, PaymentStatus.IN_PROGRESS)).willReturn(true);

        assertThatThrownBy(() -> service.createPaymentOrder(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 남의_예매에는_결제_주문을_만들_수_없다() {
        given(reservationRepository.findOwnedForUpdate(1L, 3L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPaymentOrder(1L, 3L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    // ─── 승인 시작 (T1) ───────────────────────────────────────────────────────

    @Test
    void 승인을_시작하면_IN_PROGRESS가_되고_선점이_유예만큼_늘어난다() {
        // 선점이 1분 남았다. 토스 호출 중에 만료되면 다른 요청이 좌석을 가져간다
        Reservation hold = holdWithId(1L, NOW.minusMinutes(9), 1, 1);
        Payment payment = givenPayment(hold, 50L);

        ReservationPaymentAttempt attempt = service.startPayment(1L, 2L, confirmReq(payment, 14000));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(hold.getExpiresAt()).isEqualTo(NOW.plusMinutes(3));
        assertThat(attempt.expiresAtBeforePayment()).isEqualTo(NOW.plusMinutes(1));
        assertThat(attempt.paymentId()).isEqualTo(50L);
        assertThat(attempt.amount()).isEqualTo(14000);
        assertThat(attempt.paymentKey()).isEqualTo("tgen_test_key");
    }

    @Test
    void 남은_선점이_유예보다_길면_만료_시각을_줄이지_않는다() {
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        Payment payment = givenPayment(hold, 50L);

        service.startPayment(1L, 2L, confirmReq(payment, 14000));

        assertThat(hold.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    void 금액이_주문과_다르면_PAYMENT_AMOUNT_MISMATCH이고_결제는_그대로다() {
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        Payment payment = givenPayment(hold, 50L);

        assertThatThrownBy(() -> service.startPayment(1L, 2L, confirmReq(payment, 100)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 이_예매의_주문번호가_아니면_PAYMENT_NOT_FOUND() {
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        given(reservationRepository.findOwnedForUpdate(1L, 2L)).willReturn(Optional.of(hold));
        given(paymentRepository.findByOrderIdAndReservationId("cgv-r-other", 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.startPayment(1L, 2L,
                new PaymentConfirmRequest("tgen_test_key", "cgv-r-other", 14000)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void 같은_예매의_다른_결제가_승인_중이면_PAYMENT_IN_PROGRESS() {
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        Payment payment = givenPayment(hold, 50L);
        given(paymentRepository.existsByReservationIdAndStatus(1L, PaymentStatus.IN_PROGRESS)).willReturn(true);

        assertThatThrownBy(() -> service.startPayment(1L, 2L, confirmReq(payment, 14000)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 만료된_선점은_승인을_시작할_수_없다() {
        Reservation hold = holdWithId(1L, NOW.minusMinutes(11), 1, 1);
        Payment payment = givenPayment(hold, 50L);

        assertThatThrownBy(() -> service.startPayment(1L, 2L, confirmReq(payment, 14000)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_EXPIRED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 확정된_예매는_승인을_시작할_수_없다() {
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        Payment payment = givenPayment(hold, 50L);
        hold.confirm(NOW);

        assertThatThrownBy(() -> service.startPayment(1L, 2L, confirmReq(payment, 14000)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_PENDING);
    }

    @Test
    void 거절된_결제를_다시_승인하면_INVALID_PAYMENT_STATUS() {
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        Payment payment = givenPayment(hold, 50L);
        payment.startApproval();
        payment.fail("REJECT_CARD_COMPANY", "카드사 거절");

        assertThatThrownBy(() -> service.startPayment(1L, 2L, confirmReq(payment, 14000)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);
    }

    // ─── 승인 결과 반영 (T2) ──────────────────────────────────────────────────

    @Test
    void 승인되면_결제는_DONE_예매는_RESERVED가_된다() {
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        Payment payment = paymentWithId(hold, 50L);
        payment.startApproval();
        given(paymentRepository.findById(50L)).willReturn(Optional.of(payment));
        given(reservationRepository.findOwnedWithDetails(1L, 2L)).willReturn(Optional.of(hold));
        // 토스는 +09:00으로 준다. 우리 시각대로 바꿔 저장한다
        TossPayment approved = TestFixtures.approvedToss("tgen_test_key", payment.getOrderId(), 14000,
                NOW.atZone(ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.ofHours(9)));

        ReservationResponse response = service.completePayment(attemptOf(payment, hold, NOW.plusMinutes(10)), approved);

        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(hold.getConfirmedAt()).isEqualTo(NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getPaymentKey()).isEqualTo("tgen_test_key");
        assertThat(payment.getMethod()).isEqualTo("카드");
        assertThat(payment.getApprovedAt()).isEqualTo(NOW);
    }

    @Test
    void 거절되면_결제는_FAILED_좌석은_유지되고_늘린_선점은_되돌린다() {
        Reservation hold = holdWithId(1L, NOW.minusMinutes(9), 1, 1);
        Payment payment = paymentWithId(hold, 50L);
        LocalDateTime before = hold.getExpiresAt();
        hold.holdForPayment(NOW);
        payment.startApproval();
        given(paymentRepository.findById(50L)).willReturn(Optional.of(payment));

        service.failPayment(attemptOf(payment, hold, before), "REJECT_CARD_COMPANY", "카드사 거절");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(hold.getSeats()).allMatch(ReservationSeat::isOccupied);
        // 거절을 반복해 좌석을 붙잡지 못하도록 원래 만료 시각으로 돌아간다
        assertThat(hold.getExpiresAt()).isEqualTo(before);
    }

    // ─── getById ──────────────────────────────────────────────────────────────

    @Test
    void 없는_예매_조회시_RESERVATION_NOT_FOUND() {
        given(reservationRepository.findOwnedDetailRows(99L, 2L)).willReturn(List.of());

        assertThatThrownBy(() -> service.getById(99L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 예매_조회_정상() {
        given(reservationRepository.findOwnedDetailRows(5L, 2L))
                .willReturn(List.of(detailRow(5L, NOW.plusMinutes(10), ReservationStatus.PENDING, 1, 1),
                        detailRow(5L, NOW.plusMinutes(10), ReservationStatus.PENDING, 1, 2)));

        ReservationResponse response = service.getById(5L, 2L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.seats()).extracting(ReservationResponse.SeatSummary::label)
                .containsExactly("A1", "A2");
        assertThat(response.totalPrice()).isEqualTo(28000);
        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.screening().movieTitle()).isEqualTo("범죄도시4");
        assertThat(response.screening().branchName()).isEqualTo("강남점");
    }

    @Test
    void 만료된_선점은_조회하면_EXPIRED로_보인다() {
        // DB 상태는 아직 PENDING이지만 이미 좌석을 놓은 것이나 마찬가지다
        given(reservationRepository.findOwnedDetailRows(5L, 2L))
                .willReturn(List.of(detailRow(5L, NOW.minusMinutes(10), ReservationStatus.PENDING, 1, 1)));

        assertThat(service.getById(5L, 2L).status()).isEqualTo(ReservationStatus.EXPIRED);
    }

    // ─── cancel ───────────────────────────────────────────────────────────────

    @Test
    void 없는_예매_취소시_RESERVATION_NOT_FOUND() {
        given(reservationRepository.findOwnedForUpdate(99L, 2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(99L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 취소해도_어느_좌석을_얼마에_잡았는지가_남는다() {
        Reservation hold = holdWithId(1L, NOW, 1, 1, 1, 2);
        given(reservationRepository.findOwnedForUpdate(1L, 2L)).willReturn(Optional.of(hold));

        service.cancel(1L, 2L);

        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(hold.getCancelledAt()).isEqualTo(NOW);
        assertThat(hold.getSeats()).hasSize(2);
        assertThat(hold.getTotalPrice()).isEqualTo(28000);
        assertThat(hold.getSeats()).noneMatch(ReservationSeat::isOccupied);
    }

    @Test
    void 이미_취소된_예매_취소시_ALREADY_CANCELLED() {
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        hold.cancel(NOW);
        given(reservationRepository.findOwnedForUpdate(1L, 2L)).willReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.cancel(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_CANCELLED);
    }

    @Test
    void 상영_20분_이내면_확정된_예매를_취소할_수_없다() {
        Reservation hold = holdWithId(1L, NOW, NOW.plusMinutes(19), 1, 1);
        hold.confirm(NOW);
        given(reservationRepository.findOwnedForUpdate(1L, 2L)).willReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.cancel(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANCEL_DEADLINE_PASSED);
        assertThat(hold.getSeats()).allMatch(ReservationSeat::isOccupied);
    }

    @Test
    void 승인_중인_선점은_취소할_수_없다() {
        // 토스가 승인한 뒤 확정할 예매가 없어진다
        Reservation hold = holdWithId(1L, NOW, 1, 1);
        given(reservationRepository.findOwnedForUpdate(1L, 2L)).willReturn(Optional.of(hold));
        given(paymentRepository.existsByReservationIdAndStatus(1L, PaymentStatus.IN_PROGRESS)).willReturn(true);

        assertThatThrownBy(() -> service.cancel(1L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(hold.getSeats()).allMatch(ReservationSeat::isOccupied);
    }

    @Test
    void 선점은_상영_20분_이내여도_놓을_수_있다() {
        Reservation hold = holdWithId(1L, NOW, NOW.plusMinutes(19), 1, 1);
        given(reservationRepository.findOwnedForUpdate(1L, 2L)).willReturn(Optional.of(hold));

        service.cancel(1L, 2L);

        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    // ─── 픽스처 헬퍼 ──────────────────────────────────────────────────────────

    private void givenScreeningAndUser(long screeningId, long userId) {
        given(screeningRepository.findByIdWithDetails(screeningId))
                .willReturn(Optional.of(screeningWith(screeningId, 14000, START)));
        given(userRepository.findById(userId)).willReturn(Optional.of(userWithId(userId)));
    }

    private void givenSaveAssignsId(long id) {
        given(reservationRepository.saveAndFlush(any())).willAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", id);
            return r;
        });
    }

    private Reservation holdWithId(long id, LocalDateTime createdAt, int... rowCols) {
        return holdWithId(id, createdAt, START, rowCols);
    }

    private Reservation holdWithId(long id, LocalDateTime createdAt, LocalDateTime startAt, int... rowCols) {
        Reservation reservation = Reservation.builder()
                .user(userWithId(2L))
                .screening(screeningWith(1L, 14000, startAt))
                .now(createdAt)
                .build();
        ReflectionTestUtils.setField(reservation, "id", id);
        for (int i = 0; i < rowCols.length; i += 2) {
            reservation.addSeat(rowCols[i], rowCols[i + 1], AudienceType.ADULT);
        }
        return reservation;
    }

    private Payment paymentWithId(Reservation reservation, long id) {
        Payment payment = Payment.builder().reservation(reservation).build();
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    private Payment givenPayment(Reservation reservation, long paymentId) {
        Payment payment = paymentWithId(reservation, paymentId);
        given(reservationRepository.findOwnedForUpdate(reservation.getId(), 2L)).willReturn(Optional.of(reservation));
        given(paymentRepository.findByOrderIdAndReservationId(payment.getOrderId(), reservation.getId()))
                .willReturn(Optional.of(payment));
        return payment;
    }

    private PaymentConfirmRequest confirmReq(Payment payment, int amount) {
        return new PaymentConfirmRequest("tgen_test_key", payment.getOrderId(), amount);
    }

    private ReservationPaymentAttempt attemptOf(Payment payment, Reservation reservation, LocalDateTime expiresAtBefore) {
        return new ReservationPaymentAttempt(payment.getId(), reservation.getId(), 2L, "tgen_test_key",
                payment.getOrderId(), payment.getAmount(), expiresAtBefore);
    }

    private ReservationDetailRow detailRow(long id, LocalDateTime expiresAt, ReservationStatus status,
                                          int rowNum, int colNum) {
        return new ReservationDetailRow(
                id, 2L, status, NOW, expiresAt, null, null,
                1L, "범죄도시4", "1관", "강남점", START, START.plusMinutes(120),
                rowNum, colNum, AudienceType.ADULT, 14000);
    }

    private Screening screeningWith(long id, int price, LocalDateTime startAt) {
        Branch branch = Branch.builder().name("강남점").address("서울 강남구").build();
        Theater theater = Theater.builder().branch(branch).theaterType(TheaterType.STANDARD).name("1관").build();
        Movie movie = Movie.builder()
                .title("범죄도시4").director("감독").genre("액션")
                .runningTime(120).releaseDate(LocalDate.of(2024, 1, 1)).ageRating("15세").build();
        Screening screening = Screening.builder()
                .theater(theater).movie(movie)
                .startAt(startAt).endAt(startAt.plusMinutes(120)).price(price)
                .build();
        ReflectionTestUtils.setField(screening, "id", id);
        return screening;
    }

    private User userWithId(long id) {
        User user = User.builder()
                .loginId("testuser01").password("pw").name("테스트유저")
                .birthDate(LocalDate.of(2000, 1, 1))
                .email("testuser01@test.com").phoneNumber("01012345678").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private SeatPositionProjection positionOf(int row, int col) {
        SeatPositionProjection projection = mock(SeatPositionProjection.class);
        given(projection.getRowNum()).willReturn(row);
        given(projection.getColNum()).willReturn(col);
        return projection;
    }

    private ReservationCreateRequest reqOf(long screeningId, int[]... seats) {
        List<ReservationCreateRequest.SeatRequest> seatRequests = Arrays.stream(seats)
                .map(s -> new ReservationCreateRequest.SeatRequest(s[0], s[1], AudienceType.ADULT))
                .toList();
        return new ReservationCreateRequest(screeningId, seatRequests);
    }
}
