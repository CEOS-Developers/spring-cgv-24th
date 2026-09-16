package com.ceos.cgv.domain.concession.service;

import com.ceos.cgv.domain.concession.dto.FoodOrderCreateRequest;
import com.ceos.cgv.domain.concession.dto.FoodOrderItemRequest;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "CGV_DB_LOCAL", matches = ".+")
class FoodOrderConcurrencyIntegrationTest {

    private static final long USER_ID = 801L;
    private static final long CINEMA_ID = 802L;
    private static final long PRODUCT_ID = 803L;
    private static final long INVENTORY_ID = 804L;

    @Autowired
    private FoodOrderService foodOrderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void 동시성_테스트_데이터를_준비한다() {
        cleanUp();

        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (?, ?, ?)",
                USER_ID, "재고 동시성 테스트 사용자", "food-order-concurrency@example.com");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (?, ?, ?)",
                CINEMA_ID, "재고 동시성 테스트 영화관", "서울");
        jdbcTemplate.update("INSERT INTO products (product_id, name, price, description) VALUES (?, ?, ?, ?)",
                PRODUCT_ID, "재고 테스트 상품", 1200, "테스트 상품");
        jdbcTemplate.update("INSERT INTO inventories (inventory_id, cinema_id, product_id, stock_quantity) VALUES (?, ?, ?, ?)",
                INVENTORY_ID, CINEMA_ID, PRODUCT_ID, 3);
    }

    @AfterEach
    void 동시성_테스트_데이터를_정리한다() {
        cleanUp();
    }

    @Test
    void 동시에_주문하면_최신_재고를_확인한_뒤_재고_부족으로_실패한다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inventoryLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseInventoryLock = new CountDownLatch(1);
        Future<?> firstTransaction = null;

        try {
            firstTransaction = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject(
                                "SELECT inventory_id FROM inventories WHERE inventory_id = ? FOR UPDATE",
                                Long.class,
                                INVENTORY_ID
                        );
                        inventoryLockAcquired.countDown();

                        jdbcTemplate.update("UPDATE inventories SET stock_quantity = 1 WHERE inventory_id = ?",
                                INVENTORY_ID);
                        await(releaseInventoryLock);
                    })
            );

            assertThat(inventoryLockAcquired.await(2, TimeUnit.SECONDS)).isTrue();

            Future<?> secondOrder = executor.submit(() -> foodOrderService.create(
                    new FoodOrderCreateRequest(
                            USER_ID,
                            CINEMA_ID,
                            List.of(new FoodOrderItemRequest(PRODUCT_ID, 2))
                    )
            ));

            assertThrows(TimeoutException.class, () -> secondOrder.get(1, TimeUnit.SECONDS));
            releaseInventoryLock.countDown();

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> secondOrder.get(2, TimeUnit.SECONDS)
            );
            assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) exception.getCause()).getErrorCode())
                    .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH);
        } finally {
            releaseInventoryLock.countDown();
            executor.shutdownNow();
            if (firstTransaction != null) {
                firstTransaction.get(2, TimeUnit.SECONDS);
            }
        }
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE oi FROM order_items oi JOIN food_orders fo ON fo.order_id = oi.order_id WHERE fo.user_id = ? AND fo.cinema_id = ?",
                USER_ID, CINEMA_ID);
        jdbcTemplate.update("DELETE FROM food_orders WHERE user_id = ? AND cinema_id = ?", USER_ID, CINEMA_ID);
        jdbcTemplate.update("DELETE FROM inventories WHERE inventory_id = ?", INVENTORY_ID);
        jdbcTemplate.update("DELETE FROM products WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM cinemas WHERE cinema_id = ?", CINEMA_ID);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", USER_ID);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("첫 번째 재고 트랜잭션 해제 대기 시간이 초과되었습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
