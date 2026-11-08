package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.enums.OrderStatus;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
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

class StockConcurrencyTest extends ConcurrencyTestSupport {

    private static final int REQUESTS = 10;
    private static final int STOCK = 5;

    @Autowired
    private PaymentFacade paymentFacade;

    @Test
    void 재고보다_많은_결제가_동시에_승인되면_초과분은_보상_취소된다() throws InterruptedException {
        User user = saveUser();
        Theater theater = saveTheater();
        Item item = saveItem(5_000);
        saveStock(theater, item, STOCK);
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            payments.add(paymentRepository.save(Payment.forOrder(saveOrder(user, theater, item, 1))));
        }
        when(tossPaymentClient.confirm(any(), any(), anyInt(), any())).thenAnswer(invocation -> {
            Payment payment = payments.stream().filter(p -> p.getTossOrderId().equals(invocation.getArgument(1)))
                    .findFirst().orElseThrow();
            return tossResponse("DONE", payment, payment.getAmount(), 0);
        });
        when(tossPaymentClient.cancel(any(), any(), any(), any())).thenAnswer(invocation -> {
            Payment payment = payments.stream().filter(p -> ("pk-" + p.getId()).equals(invocation.getArgument(0)))
                    .findFirst().orElseThrow();
            return tossResponse("CANCELED", payment, 0, payment.getAmount());
        });
        List<Runnable> tasks = new ArrayList<>();
        for (Payment payment : payments) {
            PaymentConfirmRequest request = new PaymentConfirmRequest("pk-" + payment.getId(), payment.getTossOrderId(),
                    payment.getAmount());
            tasks.add(() -> paymentFacade.confirm(user.getId(), request));
        }

        List<Throwable> results = runConcurrently(tasks);

        int stock = stockRepository.findByTheaterIdAndItemId(theater.getId(), item.getId()).orElseThrow().getQuantity();
        List<Order> orders = orderRepository.findAll();
        long paid = orders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
        long canceled = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELED).count();
        System.out.printf("[재고] 요청 %d, 성공 %d, 실패(PAYMENT409_1) %d, 재고 %d, PAID %d, CANCELED %d, 토스 취소 호출 %d%n",
                REQUESTS, successCount(results), failureCount(results, "PAYMENT409_1"), stock, paid, canceled, canceled);

        assertThat(stock).isZero();
        assertThat(successCount(results)).isEqualTo(STOCK);
        assertThat(failureCount(results, "PAYMENT409_1")).isEqualTo(REQUESTS - STOCK);
        assertThat(paid).isEqualTo(STOCK);
        assertThat(canceled).isEqualTo(REQUESTS - STOCK);
        verify(tossPaymentClient, times(REQUESTS - STOCK)).cancel(any(), any(), any(), any());
    }
}
