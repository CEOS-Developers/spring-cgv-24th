package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.enums.OrderStatus;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.service.PaymentFacade;
import com.ceos24.cgv.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentCancelConcurrencyTest extends ConcurrencyTestSupport {

    private static final int REQUESTS = 20;
    private static final int CANCEL_AMOUNT = 1_000;

    @Autowired
    private PaymentFacade paymentFacade;

    @Test
    void 동시에_부분_취소해도_취소_가능_잔액을_넘기지_않는다() throws InterruptedException {
        User user = saveUser();
        Item item = saveItem(10_000);
        Order order = saveOrder(user, saveTheater(), item, 1);
        order.completePayment();
        orderRepository.save(order);
        Payment payment = Payment.forOrder(order);
        payment.approve("pk-cancel", "카드", Instant.now());
        Long paymentId = paymentRepository.save(payment).getId();
        when(tossPaymentClient.cancel(any(), any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(200);
            int amount = invocation.getArgument(2);
            return tossResponse("PARTIAL_CANCELED", payment, 10_000 - amount, amount);
        });

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            String idempotencyKey = "cancel-" + UUID.randomUUID();
            tasks.add(() -> paymentFacade.cancel(user.getId(), paymentId, idempotencyKey,
                    new PaymentCancelRequest("단순 변심", CANCEL_AMOUNT)));
        }
        List<Throwable> results = runConcurrently(tasks);

        Payment result = paymentRepository.findById(paymentId).orElseThrow();
        List<PaymentCancel> cancels = paymentCancelRepository.findByPaymentIdOrderByIdAsc(paymentId);
        int doneAmount = cancels.stream().filter(c -> c.getStatus() == PaymentCancelStatus.DONE)
                .mapToInt(PaymentCancel::getCancelAmount).sum();
        System.out.printf("[부분취소] 요청 %d, 성공 %d, 실패(PAYMENT400_2) %d, 잔액 %d, 취소이력합계 %d, 실패유형 %s%n",
                REQUESTS, successCount(results), failureCount(results, "PAYMENT400_2"), result.getBalanceAmount(),
                doneAmount, failureCounts(results));

        assertThat(successCount(results)).isEqualTo(10);
        assertThat(failureCount(results, "PAYMENT400_2")).isEqualTo(10);
        assertThat(result.getBalanceAmount()).isZero();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(doneAmount).isEqualTo(10_000);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
        verify(tossPaymentClient, times(10)).cancel(any(), any(), any(), any());
    }
}
