package com.ceos24.spring_cgv.domain.payment.service;

import com.ceos24.spring_cgv.domain.member.entity.Member;
import com.ceos24.spring_cgv.domain.member.exception.MemberException;
import com.ceos24.spring_cgv.domain.member.exception.code.MemberErrorCode;
import com.ceos24.spring_cgv.domain.member.repository.MemberRepository;
import com.ceos24.spring_cgv.domain.movie.entity.ScreenType;
import com.ceos24.spring_cgv.domain.movie.entity.Screening;
import com.ceos24.spring_cgv.domain.movie.repository.ScreeningRepository;
import com.ceos24.spring_cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.spring_cgv.domain.payment.dto.SeatPosition;
import com.ceos24.spring_cgv.domain.payment.dto.CancelAttempt;
import com.ceos24.spring_cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.spring_cgv.domain.payment.dto.request.PaymentReadyRequest;
import com.ceos24.spring_cgv.domain.payment.dto.response.PaymentCancelResponse;
import com.ceos24.spring_cgv.domain.payment.dto.response.PaymentConfirmResponse;
import com.ceos24.spring_cgv.domain.payment.dto.response.PaymentInfoResponse;
import com.ceos24.spring_cgv.domain.payment.dto.response.PaymentReadyResponse;
import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentCancelResponse;
import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentConfirmResponse;
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
import com.ceos24.spring_cgv.domain.reservation.dto.ReservationSeatCommand;
import com.ceos24.spring_cgv.domain.reservation.entity.Reservation;
import com.ceos24.spring_cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.spring_cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.spring_cgv.domain.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentSeatRepository paymentSeatRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final MemberRepository memberRepository;
    private final ScreeningRepository screeningRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final TossPaymentClient tossPaymentClient;
    private final ReservationService reservationService;
    private final PaymentCancelRecorder paymentCancelRecorder;

    // 주문 번호 접두어.
    private static final String ORDER_ID_PREFIX = "cgv_";

    // 취소 멱등성 키 접두어. 승인 등 다른 작업의 키와 섞이지 않게 구분한다.
    private static final String CANCEL_KEY_PREFIX = "cancel-";

    /***
     * 함수 기능: 결제 요청 정보를 사전 등록한다. 좌석 재고를 검증하고 좌석을 점유(RESERVED)한 뒤
     *          주문 번호를 생성하여 토스 결제창에 필요한 정보를 반환한다.
     *          결제창 체류 중에는 Redis 선점(SETNX)이 같은 좌석의 동시 요청을 막는다.
     * @param memberId 결제를 요청한 회원 식별자
     * @param request 상영회차 ID와 선택한 좌석 목록
     * @return PaymentReadyResponse 주문 번호, 결제 금액, 주문명, 구매자 이름
     */
    @Transactional
    public PaymentReadyResponse ready(Long memberId, PaymentReadyRequest request) {

        Member member = getMember(memberId);

        // 금액 계산과 좌석 검증에 필요한 연관(영화, 상영관 종류)을 함께 가져온다.
        Screening screening = findScreeningById(request.screeningId());

        // 이미 시작한 회차는 예매할 수 없다.
        if (screening.getStartTime().isBefore(LocalDateTime.now())) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_SCREENING_CLOSED);
        }

        // SeatPosition(내부 DTO) 리스트를 생성한다. -> 매번 request.seats()를 통해 꺼내오면 귀찮아서.
        List<SeatPosition> seatPositions = request.seats().stream()
                .map(seat -> new SeatPosition(seat.seatRow(), seat.seatNumber()))
                .toList();

        // 요청받은 좌석이 해당 상영관에 유효한지 검증한다. (GENERAL, SPECIAL)
        validateSeatPositions(seatPositions, screening.getScreen().getScreenType());

        // 결제가 확정된 좌석이 아님을 검증한다.
        validateSeatsNotReserved(seatPositions, screening.getId());

        // orderId (토스페이먼츠에 전달한 주문번호 생성)
        String orderId = ORDER_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8);

        // 결제가 끝날 때까지 좌석을 잡아둔다. 실패하면 다른 고객이 결제 중인 좌석이다.
        if (!seatHoldRepository.hold(screening.getId(), seatPositions, orderId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_SEAT_ALREADY_HELD);
        }

        // 좌석 1석의 기본 요금 (상영 회차에 대한 요금 + 상영관에 대한 요금)
        int seatBasePrice = calculateSeatBasePrice(screening);
        // 총 요금 (좌석 1석의 요금과 연령대별 요금 고려)
        int totalAmount = calculateTotalAmount(request.seats(), seatBasePrice);

        // Payment 객체를 DB에 저장한다. (추후 토스페이먼츠 결제 승인을 보내고 받은 응답으로 orderId, orderName, amount를 검증하기 위해)
        Payment payment = paymentRepository.save(Payment.builder()
                .member(member)
                .screening(screening)
                .orderId(orderId)
                .orderName(screening.getMovie().getTitle() + " " + request.seats().size() + "석") // ex) 오디세우스 2석
                .totalAmount(totalAmount)
                .build());

        // 결제 시도 시점의 좌석/연령/금액을 스냅샷으로 남긴다.
        paymentSeatRepository.saveAll(request.seats().stream()
                .map(seat -> PaymentSeat.builder()
                        .payment(payment)
                        .seatRow(seat.seatRow())
                        .seatNumber(seat.seatNumber())
                        .ageGroup(seat.ageGroup())
                        .price(seatBasePrice + seat.ageGroup().getAdjustment())
                        .build())
                .toList());

        log.info("[결제 요청 등록] memberId={}, orderId={}, amount={}",
                memberId, orderId, totalAmount);

        return PaymentReadyResponse.from(payment);
    }

    /***
     * 함수 기능: 좌석을 확정하여 예매를 생성하고 토스에 결제 승인을 요청한다.
     * @param paymentKey 토스가 발급한 결제 키
     * @param orderId 서버가 생성한 주문 번호
     * @param amount 토스가 리다이렉트 파라미터로 보낸 금액 (대조용으로만 쓴다)
     * @return PaymentConfirmResponse 예매 ID, 주문 정보, 확정된 좌석
     */
    @Transactional
    public PaymentConfirmResponse confirm(String paymentKey, String orderId, Integer amount) {

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND));

        // 결제 요청 응답으로 전달받은 amount와 DB에 저장한 금액과 대조한다.
        if (!payment.getTotalAmount().equals(amount)) {
            log.warn("[결제 금액 불일치] orderId={}, 저장된 금액={}, 전달된 금액={}",
                    orderId, payment.getTotalAmount(), amount);
            throw new PaymentException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        List<PaymentSeat> paymentSeats = paymentSeatRepository.findAllByPaymentId(payment.getId());

        // 좌석 예매를 확정하여 DB에 저장한다.
        Reservation reservation = reservationService.reserve(
                payment.getMember(),
                payment.getScreening(),
                payment.getTotalAmount(),
                toSeatCommands(paymentSeats));

        payment.linkReservation(reservation);

        // 좌석을 확보한 뒤 토스에 결제 승인 요청을 보낸다.
        TossPaymentConfirmResponse result =
                tossPaymentClient.confirm(paymentKey, orderId, payment.getTotalAmount());

        payment.confirm(
                result.paymentKey(),
                TossPaymentMethod.from(result.method()),
                result.approvedAt().toLocalDateTime(),
                result.lastTransactionKey());

        // 좌석이 reservation_seat에 확정됐으므로 선점은 더 이상 필요 없다.
        seatHoldRepository.release(payment.getScreening().getId(), toSeatPositions(paymentSeats));

        log.info("[결제 승인 완료] orderId={}, reservationId={}, amount={}",
                orderId, reservation.getId(), payment.getTotalAmount());

        return PaymentConfirmResponse.of(payment, reservation, paymentSeats);
    }

    /***
     * 함수 기능: 토스가 failUrl로 리다이렉트했을 때 주문을 중단 처리하고 좌석 점유를 해제한다.
     * @param code 토스 에러 코드
     * @param message 토스 에러 메시지
     * @param orderId 실패한 주문 번호 (주문 생성 전에 실패하면 없을 수 있다)
     */
    @Transactional
    public void fail(String code, String message, String orderId) {

        log.warn("[결제 실패] orderId={}, code={}, message={}", orderId, code, message);

        if (orderId == null) {
            return;
        }

        // 결제를 마치지 못했으므로 ready에서 잡아둔 선점을 풀어 다른 고객이 살 수 있게 한다.
        // 예매는 confirm에서야 만들어지므로 이 시점에는 되돌릴 reservation_seat 행이 없다.
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            payment.abort();

            List<PaymentSeat> paymentSeats = paymentSeatRepository.findAllByPaymentId(payment.getId());
            seatHoldRepository.release(payment.getScreening().getId(), toSeatPositions(paymentSeats));
        });
    }

    /***
     * 함수 기능: 승인 이후 상태의 결제 1건을 조회한다. 부분/전액 취소된 결제도 내역에서 보여야 하므로 함께 조회한다.
     * @param memberId 조회를 요청한 회원 식별자
     * @param paymentId 조회할 결제 식별자
     * @return PaymentInfoResponse 주문 정보, 결제 금액/수단/상태, 취소 금액, 요청·승인 시각, 예매 ID
     */
    public PaymentInfoResponse findPayment(Long memberId, Long paymentId) {

        return PaymentInfoResponse.from(findPaymentForView(memberId, paymentId));
    }

    /***
     * 함수 기능: 승인된 결제를 취소한다. 좌석을 지정하지 않으면 아직 환불되지 않은 좌석 전체를 취소한다.
     *          토스 취소가 성공한 뒤에만 우리 상태(결제 금액/상태, 좌석 점유, 예매)를 반영한다.
     * @param memberId 취소를 요청한 회원 식별자
     * @param paymentId 취소할 결제 식별자
     * @param request 취소할 좌석 목록과 취소 사유
     * @return PaymentCancelResponse 취소 후 결제 상태와 금액, 이번에 취소된 좌석
     */
    @Transactional
    public PaymentCancelResponse cancel(Long memberId, Long paymentId, PaymentCancelRequest request) {

        Payment payment = findCancelablePayment(memberId, paymentId);

        // 상영이 시작된 회차는 좌석을 되돌릴 수 없다.
        if (payment.getScreening().getStartTime().isBefore(LocalDateTime.now())) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CANCELABLE);
        }

        // 취소할 좌석을 결정한다. PaymentCancelRequest.SeatRequest가 비어있다면 ->
        List<PaymentSeat> targets = resolveCancelTargets(paymentId, request.seats());
        // 취소할 좌석의 전체 금액
        int cancelAmount = targets.stream().mapToInt(PaymentSeat::getPrice).sum();

        // 좌석 단위로 계산하므로 초과가 나오면 좌석 데이터와 누적 취소 금액이 어긋난 것이다.
        if (cancelAmount > payment.getBalanceAmount()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED);
        }

        // 멱등성을 보장하기 위해 좌석 집합으로 Idempotent-Key를 만든다.
        // 어떤 취소인지를 좌석 집합으로 식별한다. 같은 취소의 재시도는 같은 지문을 얻는다.
        String fingerprint = cancelFingerprint(payment.getOrderId(), targets);

        validateNoConflictingPendingCancel(paymentId, fingerprint);

        // 토스에 보내기 전에 시도를 남긴다. 응답을 받지 못해도 이 기록은 살아남는다.
        CancelAttempt attempt =
                paymentCancelRecorder.record(paymentId, cancelAmount, request.reason(), fingerprint);

        TossPaymentCancelResponse result = requestCancel(payment, cancelAmount, request.reason(), attempt);

        // cancel 트랜잭션은 PaymentCancel 객체를 받은 적이 없기에 id로 새로 조회한다.
        PaymentCancel cancel = findCancelAttempt(attempt.id());
        LocalDateTime canceledAt = result.latestCancel().canceledAt().toLocalDateTime();

        cancel.complete(result.balanceAmount(), result.latestCancel().transactionKey(), canceledAt);
        payment.applyCancel(result.balanceAmount(), canceledAt);
        targets.forEach(seat -> seat.markCanceled(cancel));

        // 취소한 좌석만 점유를 풀어 다시 판매할 수 있게 한다.
        reservationService.unreserveSeats(payment.getReservation(), toReservationSeats(payment, targets));

        log.info("[결제 취소 완료] orderId={}, cancelAmount={}, balanceAmount={}, status={}",
                payment.getOrderId(), cancelAmount, result.balanceAmount(), payment.getStatus());

        return PaymentCancelResponse.of(payment, cancel, targets);
    }

    /***
     * 함수 기능: 결제 좌석 스냅샷을 예매 생성 입력으로 변환한다.
     *          ReservationService가 결제 도메인을 모르게 하려고 여기서 변환한다.
     * @param paymentSeats 결제 시점에 기록한 좌석 스냅샷
     * @return 예매 좌석 생성 명령 목록
     */
    private List<ReservationSeatCommand> toSeatCommands(List<PaymentSeat> paymentSeats) {

        return paymentSeats.stream()
                .map(seat -> new ReservationSeatCommand(
                        seat.getSeatRow(),
                        seat.getSeatNumber(),
                        seat.getAgeGroup(),
                        seat.getPrice()))
                .toList();
    }

    /***
     * 함수 기능: 결제 좌석 스냅샷을 Redis 선점 해제에 필요한 좌표 목록으로 변환한다.
     * @param paymentSeats 결제 시점에 기록한 좌석 스냅샷
     * @return 좌석 좌표 목록
     */
    private List<SeatPosition> toSeatPositions(List<PaymentSeat> paymentSeats) {

        return paymentSeats.stream()
                .map(seat -> new SeatPosition(seat.getSeatRow(), seat.getSeatNumber()))
                .toList();
    }

    /***
     * 함수 기능: 같은 좌석을 중복 선택했는지, 상영관에 존재하는 좌석인지 검사한다.
     * @param seats 요청한 좌석 좌표 목록
     * @param screenType 좌석 배치(행/열 개수)를 가진 상영관 종류
     */
    private void validateSeatPositions(List<SeatPosition> seats, ScreenType screenType) {

        // 하나의 영화 예매 요청에 중복된 좌석이 존재한다면, 에러를 반환한다.
        if (Set.copyOf(seats).size() != seats.size()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_SEAT_DUPLICATED);
        }

        // 좌석은 rowCount x colCount 직사각형으로 존재한다.
        boolean outOfRange = seats.stream().anyMatch(seat ->
                seat.seatRow() > screenType.getRowCount() || seat.seatNumber() > screenType.getColCount());

        if (outOfRange) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_SEAT_OUT_OF_RANGE);
        }
    }

    /***
     * 함수 기능: 이미 점유된(RESERVED) 좌석이 포함되어 있는지 검사한다.
     *          점유가 풀린(UNRESERVED) 좌석은 다시 판매할 수 있으므로 통과시킨다.
     * @param seats 요청한 좌석 좌표 목록
     * @param screeningId 상영회차 식별자
     */
    private void validateSeatsNotReserved(List<SeatPosition> seats, Long screeningId) {

        // 해당 상영 회차에서 점유 중인 좌석만 Set에 담는다.
        Set<SeatPosition> reserved = reservationSeatRepository.findAllByScreeningId(screeningId).stream()
                .filter(ReservationSeat::isReserved)
                .map(seat -> new SeatPosition(seat.getSeatRow(), seat.getSeatNumber()))
                .collect(Collectors.toSet());

        // 만약, 요청한 좌석 좌표 목록이 reserved 목록에 있다면 -> 예외를 반환한다.
        if (seats.stream().anyMatch(reserved::contains)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_SEAT_ALREADY_TAKEN);
        }
    }

    /***
     * 함수 기능: 연령 조정액을 적용하기 전, 좌석 1석의 기본 금액을 계산한다.
     * @param screening 상영회차
     * @return 상영회차 기본가 + 상영관 종류 추가 요금
     */
    private int calculateSeatBasePrice(Screening screening) {
        return screening.getBasePrice() + screening.getScreen().getScreenType().getExtraCharge();
    }

    /***
     * 함수 기능: 좌석별 연령 조정액을 반영한 결제 총 금액을 계산한다.
     * @param seats 요청한 좌석 목록
     * @param seatBasePrice 좌석 1석의 기본 금액
     * @return 결제 총 금액
     */
    private int calculateTotalAmount(List<PaymentReadyRequest.SeatRequest> seats, int seatBasePrice) {
        return seats.stream()
                .mapToInt(seat -> seatBasePrice + seat.ageGroup().getAdjustment())
                .sum();
    }

    private Screening findScreeningById(Long screeningId) {
        return screeningRepository.findWithDetailsById(screeningId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_SCREENING_NOT_FOUND));
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /***
     * 함수 기능: 내역에 보여도 되는 상태의 본인 결제를 조회한다.
     * @param memberId 조회를 요청한 회원 식별자
     * @param paymentId 조회할 결제 식별자
     * @return 조회할 수 있는 결제
     */
    private Payment findPaymentForView(Long memberId, Long paymentId) {

        return paymentRepository.findByIdAndMemberIdAndStatusIn(paymentId, memberId,
                        List.of(PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED, PaymentStatus.CANCELED))
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    /***
     * 함수 기능: 토스에 취소를 요청하고 실패 유형에 따라 취소 시도 기록을 정리한다.
     *          명시적 거절은 실패로 확정하고, 응답 없음은 REQUESTED로 남겨 대조 대상이 되게 한다.
     * @param payment 취소할 결제
     * @param cancelAmount 취소할 금액
     * @param reason 취소 사유
     * @param attempt 토스 호출 전에 기록한 취소 시도
     * @return 토스 취소 응답
     */
    private TossPaymentCancelResponse requestCancel(Payment payment, Integer cancelAmount,
                                                    String reason, CancelAttempt attempt) {
        try {
            return tossPaymentClient.cancel(
                    payment.getPaymentKey(), cancelAmount, reason, attempt.idempotencyKey());

        } catch (PaymentException e) {
            // 취소 여부를 모르는 경우에는 REQUESTED로 남겨야 하므로 실패로 확정하지 않는다.
            if (e.getErrorCode() != PaymentErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN) {
                paymentCancelRecorder.markFailed(attempt.id());
            }

            throw e;
        }
    }

    /***
     * 함수 기능: 취소할 좌석을 결정한다. 좌석을 지정하지 않으면 아직 환불되지 않은 좌석 전체가 대상이다.
     * @param paymentId 결제 식별자
     * @param requested 요청받은 좌석 목록 (비어 있으면 전체 취소)
     * @return 취소 대상 결제 좌석 목록
     */
    private List<PaymentSeat> resolveCancelTargets(Long paymentId,
                                                   List<PaymentCancelRequest.SeatRequest> requested) {

        List<PaymentSeat> paymentSeats = paymentSeatRepository.findAllByPaymentId(paymentId);

        // 좌석을 지정하지 않으면 전체 취소다.
        if (requested == null || requested.isEmpty()) {

            // 아직 취소되지 않은 좌석을 remaining에 넣는다.
            List<PaymentSeat> remaining = paymentSeats.stream()
                    .filter(seat -> !seat.isCanceled())
                    .toList();

            // 만약, remaining이 비었다면 -> 모두 취소되었다는 뜻이므로 에러를 반환한다.
            if (remaining.isEmpty()) {
                throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_CANCELED);
            }

            return remaining;
        }

        // 같은 좌석을 중복해서 보내면 취소 금액이 두 번 더해진다.
        if (Set.copyOf(requested).size() != requested.size()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_SEAT_DUPLICATED);
        }

        return requested.stream()
                .map(seat -> findCancelableSeat(paymentSeats, seat))
                .toList();
    }

    /***
     * 함수 기능: 요청한 좌표에 해당하는, 아직 환불되지 않은 결제 좌석을 찾는다.
     * @param paymentSeats 결제의 전체 좌석 스냅샷
     * @param requested 요청받은 좌석 좌표
     * @return 취소할 수 있는 결제 좌석
     */
    private PaymentSeat findCancelableSeat(List<PaymentSeat> paymentSeats,
                                           PaymentCancelRequest.SeatRequest requested) {

        PaymentSeat found = paymentSeats.stream()
                .filter(seat -> seat.getSeatRow().equals(requested.seatRow())
                        && seat.getSeatNumber().equals(requested.seatNumber()))
                .findFirst()
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_CANCEL_SEAT_NOT_FOUND));

        if (found.isCanceled()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_SEAT_ALREADY_CANCELED);
        }

        return found;
    }

    /***
     * 함수 기능: 취소한 결제 좌석에 대응하는 예매 좌석 행을 좌표로 찾는다.
     * @param payment 취소 중인 결제
     * @param canceled 이번에 취소한 결제 좌석
     * @return 점유를 풀어야 하는 예매 좌석 행
     */
    private List<ReservationSeat> toReservationSeats(Payment payment, List<PaymentSeat> canceled) {

        Set<SeatPosition> positions = canceled.stream()
                .map(seat -> new SeatPosition(seat.getSeatRow(), seat.getSeatNumber()))
                .collect(Collectors.toSet());

        return reservationSeatRepository.findAllByReservationId(payment.getReservation().getId()).stream()
                .filter(seat -> positions.contains(new SeatPosition(seat.getSeatRow(), seat.getSeatNumber())))
                .filter(ReservationSeat::isReserved)
                .toList();
    }

    /***
     * 함수 기능: 취소할 수 있는 상태의 본인 결제를 조회한다.
     * @param memberId 취소를 요청한 회원 식별자
     * @param paymentId 취소할 결제 식별자
     * @return 취소할 수 있는 결제
     */
    private Payment findCancelablePayment(Long memberId, Long paymentId) {

        // 결제가 완료, 부분 취소, 취소된 본인의 결제 내역을 조회한다.
        Payment payment = paymentRepository.findByIdAndMemberIdAndStatusIn(paymentId, memberId,
                        List.of(PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED, PaymentStatus.CANCELED))
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // 회원의 결제 내역이 이미 취소되었다면 '이미 취소되었습니다'를 반환한다.
        if (payment.getStatus() == PaymentStatus.CANCELED) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_CANCELED);
        }

        return payment;
    }

    /***
     * 함수 기능: 별도 트랜잭션에서 기록한 취소 시도를 잠금 조회해 영속 상태로 가져온다.
     *          일반 조회는 REPEATABLE READ 스냅샷 때문에 그 행을 보지 못한다.
     * @param cancelId 취소 시도 식별자
     * @return 영속 상태의 취소 시도
     */
    private PaymentCancel findCancelAttempt(Long cancelId) {

        return paymentCancelRepository.findWithLockById(cancelId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_CANCEL_FAILED));
    }

    /***
     * 함수 기능: 어떤 취소인지를 식별하는 지문을 만든다. 멱등성 키의 바탕이 된다.
     *          좌석 집합이 같으면 항상 같은 문자열이 나오므로, 같은 취소의 재시도가 같은 키를 쓰게 된다.
     *          금액만으로 식별하면 금액이 같은 다른 좌석을 같은 취소로 오인한다.
     * @param orderId 서버가 생성한 주문 번호
     * @param targets 취소 대상 결제 좌석
     * @return cancel-{주문번호}-{정렬된 좌석좌표} 형식의 지문
     */
    private String cancelFingerprint(String orderId, List<PaymentSeat> targets) {

        String seats = targets.stream()
                .map(seat -> seat.getSeatRow() + ":" + seat.getSeatNumber())
                .sorted()
                .collect(Collectors.joining(","));

        return CANCEL_KEY_PREFIX + orderId + "-" + seats;
    }

    /***
     * 함수 기능: 결과를 모르는 취소가 이번 요청과 다른 취소라면 거부한다.
     *          같은 취소라면 통과시켜, 같은 멱등성 키로 재시도해 토스가 원래 결과를 돌려주게 한다.
     * @param paymentId 결제 식별자
     * @param fingerprint 이번 취소의 지문
     */
    private void validateNoConflictingPendingCancel(Long paymentId, String fingerprint) {

        paymentCancelRepository.findFirstByPaymentIdAndStatus(paymentId, CancelStatus.REQUESTED)
                .filter(pending -> !pending.getRequestFingerprint().equals(fingerprint))
                .ifPresent(pending -> {
                    throw new PaymentException(PaymentErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
                });
    }
}
