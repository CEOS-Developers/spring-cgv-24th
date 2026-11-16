package com.ceos24.cgv.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceos24.cgv.domain.member.Role;
import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.order.dto.request.CreateOrderRequest;
import com.ceos24.cgv.domain.order.dto.request.OrderItemRequest;
import com.ceos24.cgv.domain.order.repository.OrderItemRepository;
import com.ceos24.cgv.domain.order.repository.OrderRepository;
import com.ceos24.cgv.domain.store.entity.Menu;
import com.ceos24.cgv.domain.store.entity.MenuStock;
import com.ceos24.cgv.domain.store.entity.Store;
import com.ceos24.cgv.domain.store.repository.MenuRepository;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.domain.store.repository.StoreRepository;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TheaterRepository theaterRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private MenuStockRepository menuStockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private Long memberId;
    private Long storeId;
    private Long menuId;

    @BeforeEach
    void setUp() {
        tearDown();

        Member member = memberRepository.save(Member.builder()
                .name("동시성테스터")
                .email("concurrency@cgv.com")
                .loginId("concurrency_user")
                .password("password1234!")
                .role(Role.USER)
                .build());
        memberId = member.getId();

        Theater theater = theaterRepository.save(new Theater("CGV 강남", "서울시 강남구"));
        Store store = storeRepository.save(new Store(theater, "강남 매점"));
        storeId = store.getId();

        Menu menu = menuRepository.save(new Menu("고소팝콘L", 6000L));
        menuId = menu.getId();
    }

    @AfterEach
    void tearDown() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        menuStockRepository.deleteAll();
        menuRepository.deleteAll();
        storeRepository.deleteAll();
        theaterRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("[비관적 락 적용] 동시에 100명이 1개씩 주문할 때 정합성 100% 보장 검증")
    void concurrencyOrderTest_WithPessimisticLock() throws InterruptedException {
        // given: 재고 100개 세팅
        Menu menu = menuRepository.findById(menuId).orElseThrow();
        Store store = storeRepository.findById(storeId).orElseThrow();
        MenuStock menuStock = menuStockRepository.save(new MenuStock(menu, store, 100L));
        Long targetStockId = menuStock.getId();

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        OrderItemRequest itemRequest = new OrderItemRequest(menuId, 1L);
        CreateOrderRequest request = new CreateOrderRequest(storeId, List.of(itemRequest));

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기
                    orderService.createOrder(memberId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();

        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        MenuStock finalStock = menuStockRepository.findById(targetStockId).orElseThrow();

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(finalStock.getStock()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[비관적 락 적용] 재고(10개) 초과 50명 동시 요청 시 정확히 10개만 성공하고 40개 실패(OUT_OF_STOCK) 방어")
    void concurrencyOrder_OversellingDefense_Test() throws InterruptedException {
        // given: 재고 10개 세팅
        Menu menu = menuRepository.findById(menuId).orElseThrow();
        Store store = storeRepository.findById(storeId).orElseThrow();
        MenuStock menuStock = menuStockRepository.save(new MenuStock(menu, store, 10L));
        Long targetStockId = menuStock.getId();

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        OrderItemRequest itemRequest = new OrderItemRequest(menuId, 1L);
        CreateOrderRequest request = new CreateOrderRequest(storeId, List.of(itemRequest));

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    orderService.createOrder(memberId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();

        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        MenuStock finalStock = menuStockRepository.findById(targetStockId).orElseThrow();

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(40);
        assertThat(finalStock.getStock()).isEqualTo(0L);
    }
}
