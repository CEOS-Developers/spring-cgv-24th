package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.concession.dto.request.OrderCreateRequest;
import com.ceos24.cgv.domain.concession.dto.request.OrderItemRequest;
import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.entity.OrderItem;
import com.ceos24.cgv.domain.concession.entity.Stock;
import com.ceos24.cgv.domain.concession.service.OrderService;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.exception.GeneralException;
import com.ceos24.cgv.support.SqlCountInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@TestPropertySource(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ceos24.cgv.support.SqlCountInspector")
class OrderCreateQueryCountIntegrationTest extends ConcurrencyTestSupport {

    private static final int WARMUP_RUNS = 10;
    private static final int TIMING_RUNS = 30;
    private static final int LOOKUP_RUNS = 200;
    private static final long MISSING_ITEM_ID = Long.MAX_VALUE;

    @Autowired
    private OrderService orderService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10})
    void 주문_생성시_재고는_상품_종류_수와_관계없이_한_번에_조회한다(int itemCount) {
        User user = saveUser();
        Theater theater = saveTheater();
        List<OrderItemRequest> items = new ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            Item item = saveItem(1_000 * i);
            saveStock(theater, item, 10_000);
            items.add(new OrderItemRequest(item.getId(), 1));
        }
        OrderCreateRequest request = new OrderCreateRequest(theater.getId(), items);

        SqlCountInspector.clear();
        orderService.create(user.getId(), request);
        long stockSelects = SqlCountInspector.selectCount("stock");
        int totalSql = SqlCountInspector.total();
        SqlCountInspector.statements().stream().distinct().forEach(sql -> System.out.println("[ORDER-CREATE-SQL] " + sql));

        for (int i = 0; i < WARMUP_RUNS; i++) {
            orderService.create(user.getId(), request);
        }
        List<Long> elapsedNanos = new ArrayList<>();
        for (int i = 0; i < TIMING_RUNS; i++) {
            long start = System.nanoTime();
            orderService.create(user.getId(), request);
            elapsedNanos.add(System.nanoTime() - start);
        }
        elapsedNanos.sort(Comparator.naturalOrder());
        System.out.printf("[ORDER-CREATE] items=%d stockSelect=%d totalSql=%d medianMs=%.3f minMs=%.3f maxMs=%.3f%n",
                itemCount, stockSelects, totalSql, elapsedNanos.get(TIMING_RUNS / 2) / 1e6,
                elapsedNanos.getFirst() / 1e6, elapsedNanos.getLast() / 1e6);

        assertThat(stockSelects).isEqualTo(1);
        assertThat(totalSql).isEqualTo(itemCount + 6);
    }

    // 같은 JVM·DB 상태에서 기존 방식(상품별 단건 조회)과 IN 일괄 조회를 번갈아 실행해 재고 조회 구간만 비교한다
    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10})
    void 재고_IN_일괄_조회는_상품별_단건_조회와_같은_재고를_반환한다(int itemCount) {
        Theater theater = saveTheater();
        List<Long> itemIds = new ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            Item item = saveItem(1_000 * i);
            saveStock(theater, item, 10);
            itemIds.add(item.getId());
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Supplier<List<Long>> single = () -> transactionTemplate.execute(status -> itemIds.stream()
                .map(itemId -> stockRepository.findByTheaterIdAndItemId(theater.getId(), itemId).orElseThrow().getId())
                .toList());
        Supplier<List<Long>> batch = () -> transactionTemplate.execute(status -> stockRepository
                .findByTheaterIdAndItemIdIn(theater.getId(), itemIds).stream().map(Stock::getId).toList());

        List<Long> singleNanos = new ArrayList<>();
        List<Long> batchNanos = new ArrayList<>();
        for (int i = 0; i < WARMUP_RUNS + LOOKUP_RUNS; i++) {
            boolean singleFirst = i % 2 == 0;
            long first = elapsedNanos(singleFirst ? single : batch);
            long second = elapsedNanos(singleFirst ? batch : single);
            if (i >= WARMUP_RUNS) {
                singleNanos.add(singleFirst ? first : second);
                batchNanos.add(singleFirst ? second : first);
            }
        }
        System.out.printf("[STOCK-LOOKUP] items=%d singleMedianMs=%.3f batchMedianMs=%.3f (n=%d)%n",
                itemCount, median(singleNanos) / 1e6, median(batchNanos) / 1e6, LOOKUP_RUNS);

        assertThat(batch.get()).containsExactlyInAnyOrderElementsOf(single.get());
    }

    @Test
    void 같은_상품은_수량을_합산하고_재고를_한_번에_조회해_주문을_생성한다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item popcorn = saveItem(5_000);
        Item cola = itemRepository.save(Item.create("콜라", 2_500));
        saveStock(theater, popcorn, 10);
        saveStock(theater, cola, 10);

        SqlCountInspector.clear();
        Long orderId = orderService.create(user.getId(), new OrderCreateRequest(theater.getId(), List.of(
                new OrderItemRequest(popcorn.getId(), 1),
                new OrderItemRequest(cola.getId(), 1),
                new OrderItemRequest(popcorn.getId(), 2))));

        assertThat(SqlCountInspector.selectCount("stock")).isEqualTo(1);
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getTotalPrice()).isEqualTo(17_500);
        assertThat(orderItemRepository.findByOrderIdWithItem(orderId))
                .extracting(orderItem -> orderItem.getItem().getId(), OrderItem::getQuantity)
                .containsExactlyInAnyOrder(
                        tuple(popcorn.getId(), 3),
                        tuple(cola.getId(), 1));
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getAmount()).isEqualTo(17_500);
    }

    @Test
    void 상품이_없으면_주문을_생성하지_않는다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item popcorn = saveItem(5_000);
        saveStock(theater, popcorn, 10);

        assertThatThrownBy(() -> orderService.create(user.getId(), new OrderCreateRequest(theater.getId(), List.of(
                new OrderItemRequest(popcorn.getId(), 1), new OrderItemRequest(MISSING_ITEM_ID, 1)))))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("ITEM404"));
        assertNothingSaved();
    }

    @Test
    void 영화관에_재고가_없는_상품이면_주문을_생성하지_않는다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item popcorn = saveItem(5_000);
        Item cola = itemRepository.save(Item.create("콜라", 2_500));
        saveStock(theater, popcorn, 10);

        assertThatThrownBy(() -> orderService.create(user.getId(), new OrderCreateRequest(theater.getId(), List.of(
                new OrderItemRequest(popcorn.getId(), 1), new OrderItemRequest(cola.getId(), 1)))))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("STOCK404"));
        assertNothingSaved();
    }

    @Test
    void 합산한_수량보다_재고가_적으면_주문을_생성하지_않는다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item popcorn = saveItem(5_000);
        saveStock(theater, popcorn, 2);

        assertThatThrownBy(() -> orderService.create(user.getId(), new OrderCreateRequest(theater.getId(), List.of(
                new OrderItemRequest(popcorn.getId(), 2), new OrderItemRequest(popcorn.getId(), 1)))))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("STOCK409"));
        assertNothingSaved();
    }

    @Test
    void 요청한_상품_순서대로_검증해_먼저_실패한_오류를_반환한다() {
        User user = saveUser();
        Theater theater = saveTheater();
        Item cola = itemRepository.save(Item.create("콜라", 2_500));

        assertThatThrownBy(() -> orderService.create(user.getId(), new OrderCreateRequest(theater.getId(), List.of(
                new OrderItemRequest(cola.getId(), 1), new OrderItemRequest(MISSING_ITEM_ID, 1)))))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("STOCK404"));
        assertThatThrownBy(() -> orderService.create(user.getId(), new OrderCreateRequest(theater.getId(), List.of(
                new OrderItemRequest(MISSING_ITEM_ID, 1), new OrderItemRequest(cola.getId(), 1)))))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(errorCode(e)).isEqualTo("ITEM404"));
        assertNothingSaved();
    }

    private static long elapsedNanos(Supplier<List<Long>> task) {
        long start = System.nanoTime();
        task.get();
        return System.nanoTime() - start;
    }

    private static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private void assertNothingSaved() {
        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
    }
}
