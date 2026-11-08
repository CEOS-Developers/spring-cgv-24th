package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.enums.OrderStatus;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.service.PaymentFacade;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentConfirmConcurrencyTest extends ConcurrencyTestSupport {

    private static final int REQUESTS = 10;

    @Autowired
    private PaymentFacade paymentFacade;

    @Test
    void 같은_결제를_동시에_승인해도_토스_승인은_한_번만_호출된다() throws InterruptedException {
        User user = saveUser();
        Theater theater = saveTheater();
        Item item = saveItem(5_000);
        saveStock(theater, item, 10);
        Order order = saveOrder(user, theater, item, 2);
        Payment payment = paymentRepository.save(Payment.forOrder(order));
        when(tossPaymentClient.confirm(any(), any(), anyInt(), any())).thenAnswer(invocation -> {
            Thread.sleep(50);
            return tossResponse("DONE", payment, payment.getAmount(), 0);
        });
        PaymentConfirmRequest request = new PaymentConfirmRequest("pk-" + payment.getId(), payment.getTossOrderId(),
                payment.getAmount());
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            tasks.add(() -> paymentFacade.confirm(user.getId(), request));
        }

        List<Throwable> results = runConcurrently(tasks);

        Payment result = paymentRepository.findById(payment.getId()).orElseThrow();
        int stock = stockRepository.findByTheaterIdAndItemId(theater.getId(), item.getId()).orElseThrow().getQuantity();
        System.out.printf("[중복승인] 요청 %d, 성공 %d, 실패(PAYMENT409) %d, 토스 승인 호출 1회, 결제상태 %s, 재고 %d%n",
                REQUESTS, successCount(results), failureCount(results, "PAYMENT409"), result.getStatus(), stock);

        verify(tossPaymentClient, times(1)).confirm(any(), any(), anyInt(), any());
        assertThat(successCount(results) + failureCount(results, "PAYMENT409")).isEqualTo(REQUESTS);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(stock).isEqualTo(8);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
