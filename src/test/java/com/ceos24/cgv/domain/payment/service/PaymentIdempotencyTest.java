package com.ceos24.cgv.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ceos24.cgv.domain.member.Role;
import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.order.entity.OrderItem;
import com.ceos24.cgv.domain.order.repository.OrderRepository;
import com.ceos24.cgv.domain.payment.dto.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.payment.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancelHistory;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelHistoryRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.store.entity.Menu;
import com.ceos24.cgv.domain.store.entity.Store;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentCancelHistoryRepository paymentCancelHistoryRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TossPaymentsClient tossPaymentsClient;

    private Order order;
    private Payment readyPayment;
    private final String orderId = "CGV_ORDER_TEST_1001";
    private final String paymentKey = "pk_test_concurrency_key_123";
    private final Long totalAmount = 15000L;

    @BeforeEach
    void setUp() {
        Member member = Member.builder()
                .name("테스터")
                .email("test@cgv.com")
                .loginId("testuser")
                .password("pass1234!")
                .role(Role.USER)
                .build();
        Theater theater = new Theater("CGV 강남", "서울시 강남구");
        Store store = new Store(theater, "강남 매점");
        Menu menu1 = new Menu("고소팝콘L", 6000L);
        Menu menu2 = new Menu("탄산음료M", 3000L);

        OrderItem item1 = OrderItem.create(menu1, 2L); // 12,000원
        OrderItem item2 = OrderItem.create(menu2, 1L); // 3,000원
        order = Order.create(member, store, List.of(item1, item2));

        readyPayment = Payment.createReady(order, orderId, totalAmount);
    }

    @Test
    @DisplayName("[승인 멱등성] 동일한 결제 승인 요청이 2회 유입되어도 최초 1회만 토스를 호출하고 2회차는 멱등 반환한다")
    void confirmPayment_IdempotencyTest() {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest(paymentKey, orderId, totalAmount);
        TossPaymentResponse mockTossResponse = new TossPaymentResponse(
                paymentKey,
                orderId,
                "DONE",
                "카드",
                totalAmount,
                totalAmount,
                OffsetDateTime.now().toString());

        given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(readyPayment));
        given(tossPaymentsClient.confirm(any(TossConfirmRequest.class), eq(orderId)))
                .willReturn(mockTossResponse);

        // 1. 최초 결제 승인 요청 실행
        PaymentResponse firstResponse = paymentService.confirmPayment(request);

        assertThat(firstResponse.status()).isEqualTo(PaymentStatus.DONE);
        assertThat(firstResponse.paymentKey()).isEqualTo(paymentKey);
        assertThat(firstResponse.balanceAmount()).isEqualTo(totalAmount);
        verify(tossPaymentsClient, times(1)).confirm(any(), eq(orderId));

        // 2. 네트워크 지연/재시도로 동일한 결제 승인 요청 2차 인입
        PaymentResponse secondResponse = paymentService.confirmPayment(request);

        // then: 상태와 결과는 동일하며, 토스 승인 API는 추가 호출되지 않고(호출 횟수 1회 유지) 멱등하게 반환됨
        assertThat(secondResponse.status()).isEqualTo(PaymentStatus.DONE);
        assertThat(secondResponse.paymentKey()).isEqualTo(paymentKey);
        assertThat(secondResponse.balanceAmount()).isEqualTo(totalAmount);
        verify(tossPaymentsClient, times(1)).confirm(any(), eq(orderId)); // 2차에는 토스 호출 0회!
    }

    @Test
    @DisplayName("[승인 위변조 방어] 이미 승인 완료된 결제에 대해 금액이 변조된 재요청이 오면 예외를 던진다")
    void confirmPayment_TamperedAmount_ThrowsException() {
        // given: 이미 승인 완료(DONE)된 결제
        readyPayment.complete(paymentKey, "카드", LocalDateTime.now());
        given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(readyPayment));

        // 위변조된 요청: 결제 금액을 10,000원으로 조작
        PaymentConfirmRequest tamperedRequest = new PaymentConfirmRequest(paymentKey, orderId, 10000L);

        // when & then
        assertThatThrownBy(() -> paymentService.confirmPayment(tamperedRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(tossPaymentsClient, never()).confirm(any(), any());
    }

    @Test
    @DisplayName("[부분 취소 멱등성] 동일한 cancelRequestId로 2회 취소 요청 시 1회차만 잔액이 차감되고 2회차는 과환불이 방어된다")
    void cancelPayment_Idempotency_PreventsOverCancellation() {
        // given: 승인 완료(DONE) 상태의 결제
        readyPayment.complete(paymentKey, "카드", LocalDateTime.now());
        given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(readyPayment));

        String cancelRequestId = "cancel_uuid_req_001";
        PaymentCancelRequest cancelRequest = new PaymentCancelRequest(cancelRequestId, "고객 단순 변심", 5000L, null, null);

        TossPaymentResponse mockTossCancelResponse = new TossPaymentResponse(
                paymentKey,
                orderId,
                "PARTIAL_CANCELED",
                "카드",
                totalAmount,
                10000L,
                OffsetDateTime.now().toString());
        given(tossPaymentsClient.cancel(eq(paymentKey), any(), eq(cancelRequestId)))
                .willReturn(mockTossCancelResponse);
        given(paymentCancelHistoryRepository.findByCancelRequestId(cancelRequestId))
                .willReturn(Optional.empty());

        // 1. 최초 부분 취소 요청 (5,000원 취소)
        PaymentResponse firstCancelResponse = paymentService.cancelPayment(paymentKey, cancelRequest);

        assertThat(firstCancelResponse.balanceAmount()).isEqualTo(10000L);
        assertThat(firstCancelResponse.status()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        verify(tossPaymentsClient, times(1)).cancel(eq(paymentKey), any(), eq(cancelRequestId));

        // 2. 취소 완료 후 네트워크 타임아웃으로 클라이언트가 동일한 cancelRequestId로 2차 재시도
        // (가맹점 DB에는 cancelRequestId가 이미 기록된 상태를 모킹)
        PaymentCancelHistory existingHistory = mock(PaymentCancelHistory.class);
        given(paymentCancelHistoryRepository.findByCancelRequestId(cancelRequestId))
                .willReturn(Optional.of(existingHistory));

        PaymentResponse secondCancelResponse = paymentService.cancelPayment(paymentKey, cancelRequest);

        // then: 잔액이 5,000원으로 또 깎이지 않고 10,000원으로 안전하게 유지되며, 토스 취소 호출도 1회로 유지됨 (과환불 원천 차단)
        assertThat(secondCancelResponse.balanceAmount()).isEqualTo(10000L);
        verify(tossPaymentsClient, times(1)).cancel(eq(paymentKey), any(), eq(cancelRequestId)); // 추가 호출 0회!
    }

    @Test
    @DisplayName("[부분 취소 신규 요청] 서로 다른 cancelRequestId의 취소 요청은 정상적으로 각각 차감된다")
    void cancelPayment_DifferentCancelRequestId_ProceedsAsNewCancellation() {
        // given: 승인 완료(DONE) 상태의 결제
        readyPayment.complete(paymentKey, "카드", LocalDateTime.now());
        given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(readyPayment));

        String cancelReq1 = "cancel_req_001";
        String cancelReq2 = "cancel_req_002";
        PaymentCancelRequest request1 = new PaymentCancelRequest(cancelReq1, "1차 취소", 5000L, null, null);
        PaymentCancelRequest request2 = new PaymentCancelRequest(cancelReq2, "2차 취소", 5000L, null, null);

        TossPaymentResponse mockTossResponse = new TossPaymentResponse(
                paymentKey,
                orderId,
                "PARTIAL_CANCELED",
                "카드",
                totalAmount,
                10000L,
                OffsetDateTime.now().toString());
        given(tossPaymentsClient.cancel(eq(paymentKey), any(), any())).willReturn(mockTossResponse);
        given(paymentCancelHistoryRepository.findByCancelRequestId(any())).willReturn(Optional.empty());

        // 1. 1차 취소 실행 (5,000원 차감)
        PaymentResponse response1 = paymentService.cancelPayment(paymentKey, request1);
        assertThat(response1.balanceAmount()).isEqualTo(10000L);

        // 2. 2차 다른 취소 실행 (추가 5,000원 차감)
        PaymentResponse response2 = paymentService.cancelPayment(paymentKey, request2);
        assertThat(response2.balanceAmount()).isEqualTo(5000L);

        // then: 토스 취소 API가 각 요청마다 1회씩 총 2회 정상 호출됨
        verify(tossPaymentsClient, times(2)).cancel(eq(paymentKey), any(), any());
    }
}
