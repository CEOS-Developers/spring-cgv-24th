package com.ceos24.springboot.shop;

import com.ceos24.springboot.auth.jwt.JwtProvider;
import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.dto.*;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.payment.service.PaymentConfirmService;
import com.ceos24.springboot.payment.service.PaymentService;
import com.ceos24.springboot.payment.toss.TossPaymentClient;
import com.ceos24.springboot.shop.domain.*;
import com.ceos24.springboot.shop.dto.*;
import com.ceos24.springboot.shop.repository.StoreOrderRepository;
import com.ceos24.springboot.shop.service.*;
import com.ceos24.springboot.theater.domain.Region;
import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:store-order-payment-test;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StoreOrderPaymentIntegrationTest {
    @Autowired private ShopService shop;
    @Autowired private PaymentService preparation;
    @Autowired private PaymentConfirmService approval;
    @Autowired private StoreOrderExpirationService expiration;
    @Autowired private StoreOrderExpirationScheduler scheduler;
    @Autowired private StoreOrderRepository orders;
    @Autowired private PaymentRepository payments;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;
    @Autowired private JwtProvider jwt;
    @MockitoBean(enforceOverride = true) private TossPaymentClient toss;

    private Long userId;
    private Long otherUserId;
    private Long theaterId;
    private Long popcornId;
    private Long drinkId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            userId = persist(User.create("구매자", UUID.randomUUID() + "@shop.test", "unused", "01012345678")).getId();
            otherUserId = persist(User.create("다른 구매자", UUID.randomUUID() + "@shop.test", "unused", "01012345679")).getId();
            Theater theater = persist(Theater.create("매점 테스트", Region.Seoul));
            theaterId = theater.getTheaterId();
            Menu popcorn = menu("팝콘", 6000);
            Menu drink = menu("음료", 3000);
            popcornId = popcorn.getMenuId();
            drinkId = drink.getMenuId();
            persist(StoreInventory.builder().theater(theater).menu(popcorn).stock(10).build());
            persist(StoreInventory.builder().theater(theater).menu(drink).stock(10).build());
        });
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            for (String table : List.of("payment", "order_item", "store_order", "store_inventory", "menu", "theater", "cgv_user")) {
                jdbc.update("delete from " + table);
            }
        });
    }

    @Test
    void orderCreationHoldsStockAndSnapshotsPricesAndMergesDuplicateMenus() {
        StoreOrderResponse order = shop.createOrder(userId, new StoreOrderCreateRequest(theaterId, List.of(
                new OrderItemRequest(drinkId, 1), new OrderItemRequest(popcornId, 1), new OrderItemRequest(popcornId, 2))));
        assertThat(order.status()).isEqualTo(StoreOrderStatus.PENDING);
        assertThat(order.expiresAt()).isAfter(LocalDateTime.now());
        assertThat(order.items()).hasSize(2);
        assertThat(order.totalAmount()).isEqualTo(21000);
        assertThat(stock(popcornId)).isEqualTo(7);
        assertThat(stock(drinkId)).isEqualTo(9);

        jdbc.update("update menu set menu_price = 9999 where menu_id = ?", popcornId);
        StoreOrderResponse saved = shop.getOrder(userId, order.orderId());
        assertThat(saved.totalAmount()).isEqualTo(21000);
        assertThat(saved.items().stream().filter(item -> item.menuId().equals(popcornId)).findFirst().orElseThrow().menuPrice())
                .isEqualTo(6000);
    }

    @Test
    void sharedApprovalEndpointPaysStoreOrderOnceAndPreservesInventory() throws Exception {
        String body = mvc.perform(post("/api/shop/orders").header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(2))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        StoreOrderResponse order = json.readValue(body, StoreOrderResponse.class);
        String preparedJson = mvc.perform(post("/api/payments/store-orders/prepare")
                        .header("Authorization", bearer(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new StoreOrderPaymentPrepareRequest(order.orderId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderName").value("매점 상품 주문"))
                .andReturn().getResponse().getContentAsString();
        var prepared = json.readValue(preparedJson, PaymentPrepareResponse.class);
        assertThat(preparation.prepareStoreOrder(userId, new StoreOrderPaymentPrepareRequest(order.orderId())))
                .isEqualTo(prepared);
        var confirm = stub(prepared, "DONE");

        String first = confirmHttp(confirm);
        backdate(order.orderId());
        String repeated = confirmHttp(confirm);
        assertThat(json.readValue(repeated, PaymentConfirmResponse.class))
                .isEqualTo(json.readValue(first, PaymentConfirmResponse.class));
        assertThat(shop.getOrder(userId, order.orderId()).status()).isEqualTo(StoreOrderStatus.PAID);
        assertThat(stock(popcornId)).isEqualTo(8);
        assertThat(payments.count()).isEqualTo(1);
        assertError(() -> shop.cancelOrder(userId, order.orderId()), ErrorCode.PAID_STORE_ORDER_CANCEL_NOT_SUPPORTED);
        verify(toss, times(1)).confirm(any(), anyString());
        verifyNoMoreInteractions(toss);
    }

    @Test
    void otherUserAndAnonymousCannotPrepareApproveReadOrCancelOrder() throws Exception {
        var order = create(2);
        var confirm = prepare(order.orderId(), "DONE");
        assertError(() -> preparation.prepareStoreOrder(otherUserId, new StoreOrderPaymentPrepareRequest(order.orderId())),
                ErrorCode.STORE_ORDER_NOT_FOUND);
        assertError(() -> approval.confirm(otherUserId, confirm), ErrorCode.PAYMENT_NOT_FOUND);
        assertError(() -> shop.getOrder(otherUserId, order.orderId()), ErrorCode.STORE_ORDER_NOT_FOUND);
        assertError(() -> shop.cancelOrder(otherUserId, order.orderId()), ErrorCode.STORE_ORDER_NOT_FOUND);
        mvc.perform(post("/api/payments/store-orders/prepare").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new StoreOrderPaymentPrepareRequest(order.orderId()))))
                .andExpect(status().isUnauthorized());
        assertThat(stock(popcornId)).isEqualTo(8);
        verifyNoInteractions(toss);
    }

    @Test
    void cancellationRestoresStockOnceAndClosesPreparedPayment() {
        var order = create(3);
        var confirm = prepare(order.orderId(), "DONE");
        assertThat(shop.cancelOrder(userId, order.orderId()).status()).isEqualTo(StoreOrderStatus.CANCELED);
        assertThat(shop.cancelOrder(userId, order.orderId()).status()).isEqualTo(StoreOrderStatus.CANCELED);
        scheduler.expirePendingOrders();
        assertThat(stock(popcornId)).isEqualTo(10);
        assertThat(paymentStatus(confirm.orderId())).isEqualTo(PaymentStatus.CANCELED);
        assertError(() -> approval.confirm(userId, confirm), ErrorCode.INVALID_PAYMENT_STATUS);
        verifyNoInteractions(toss);
    }

    @Test
    void expirationRestoresStockOnceAndClosesPreparedPayment() {
        var order = create(3);
        var confirm = prepare(order.orderId(), "DONE");
        backdate(order.orderId());
        scheduler.expirePendingOrders();
        expiration.expireOrder(order.orderId());
        assertThat(shop.getOrder(userId, order.orderId()).status()).isEqualTo(StoreOrderStatus.EXPIRED);
        assertThat(stock(popcornId)).isEqualTo(10);
        assertThat(paymentStatus(confirm.orderId())).isEqualTo(PaymentStatus.EXPIRED);
        assertError(() -> preparation.prepareStoreOrder(userId, new StoreOrderPaymentPrepareRequest(order.orderId())),
                ErrorCode.STORE_ORDER_EXPIRED);
        verifyNoInteractions(toss);
    }

    @Test
    void newOrderReleasesExpiredStockWithoutWaitingForScheduler() {
        var old = create(8);
        backdate(old.orderId());
        assertThat(create(9).status()).isEqualTo(StoreOrderStatus.PENDING);
        assertThat(orders.findById(old.orderId()).orElseThrow().getStatus()).isEqualTo(StoreOrderStatus.EXPIRED);
        assertThat(stock(popcornId)).isEqualTo(1);
    }

    @Test
    void failedMultiItemOrderRollsBackAllStockChanges() {
        assertError(() -> shop.createOrder(userId, new StoreOrderCreateRequest(theaterId, List.of(
                new OrderItemRequest(popcornId, 2), new OrderItemRequest(drinkId, 11)))), ErrorCode.INSUFFICIENT_STOCK);
        assertThat(stock(popcornId)).isEqualTo(10);
        assertThat(stock(drinkId)).isEqualTo(10);
        assertThat(orders.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidQuantityDoesNotChangeStock(int quantity) {
        assertError(() -> create(quantity), ErrorCode.INVALID_STORE_ORDER_REQUEST);
        assertThat(stock(popcornId)).isEqualTo(10);
        assertThat(orders.count()).isZero();
    }

    @Test
    void forgedApprovalAmountIsRejectedBeforeGatewayCall() {
        var order = create(2);
        var request = prepare(order.orderId(), "DONE");
        assertError(() -> approval.confirm(userId, new PaymentConfirmRequest(request.paymentKey(), request.orderId(), 1L)),
                ErrorCode.PAYMENT_INFORMATION_MISMATCH);
        assertThat(shop.getOrder(userId, order.orderId()).status()).isEqualTo(StoreOrderStatus.PENDING);
        assertThat(stock(popcornId)).isEqualTo(8);
        verifyNoInteractions(toss);
    }

    @Test
    void concurrentOrdersForLastItemHaveExactlyOneWinner() throws Exception {
        jdbc.update("update store_inventory set stock = 1 where menu_id = ?", popcornId);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> buyLastItem(start, userId));
            var second = executor.submit(() -> buyLastItem(start, otherUserId));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(stock(popcornId)).isZero();
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void approvalCrossingDeadlineKeepsStockAndBlocksCancellationUntilPaid() throws Exception {
        var order = create(10);
        var request = prepare(order.orderId(), "DONE");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("approval wait timed out");
            return remote(request, "DONE");
        }).when(toss).confirm(any(), anyString());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> approval.confirm(userId, request));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                backdate(order.orderId());
                scheduler.expirePendingOrders();
                expiration.expireOrder(order.orderId());
                assertThat(shop.getOrder(userId, order.orderId()).paymentProcessing()).isTrue();
                assertError(() -> shop.cancelOrder(userId, order.orderId()), ErrorCode.PAYMENT_IN_PROGRESS);
                assertError(() -> create(1), ErrorCode.INSUFFICIENT_STOCK);
                assertThat(stock(popcornId)).isZero();
            } finally {
                release.countDown();
            }
            assertThat(future.get(5, TimeUnit.SECONDS).storeOrderStatus()).isEqualTo(StoreOrderStatus.PAID);
        }
        assertThat(stock(popcornId)).isZero();
        assertThat(shop.getOrder(userId, order.orderId()).paymentProcessing()).isFalse();
    }

    @Test
    void timeoutKeepsStockAndRetryUsesSameIdempotencyKeyEvenAfterDeadline() {
        var order = create(2);
        var request = prepare(order.orderId(), "DONE");
        when(toss.confirm(any(), anyString())).thenThrow(new ResourceAccessException("simulated timeout"))
                .thenReturn(remote(request, "DONE"));
        when(toss.findByPaymentKey(request.paymentKey())).thenThrow(new ResourceAccessException("simulated lookup timeout"));
        assertError(() -> approval.confirm(userId, request), ErrorCode.PAYMENT_RESULT_UNKNOWN);
        assertThat(paymentStatus(request.orderId())).isEqualTo(PaymentStatus.UNKNOWN);
        backdate(order.orderId());
        scheduler.expirePendingOrders();
        assertThat(stock(popcornId)).isEqualTo(8);
        assertError(() -> shop.cancelOrder(userId, order.orderId()), ErrorCode.PAYMENT_IN_PROGRESS);

        assertThat(approval.confirm(userId, request).storeOrderStatus()).isEqualTo(StoreOrderStatus.PAID);
        String key = payments.findAll().getFirst().getConfirmIdempotencyKey();
        verify(toss, times(2)).confirm(new TossPaymentConfirmRequest(request.paymentKey(), request.orderId(), request.amount()), key);
        assertThat(stock(popcornId)).isEqualTo(8);
    }

    @Test
    void confirmedFailureAfterDeadlineRestoresStockOnce() {
        var order = create(2);
        var request = prepare(order.orderId(), "ABORTED");
        doAnswer(invocation -> {
            backdate(order.orderId());
            return remote(request, "ABORTED");
        }).when(toss).confirm(any(), anyString());
        assertError(() -> approval.confirm(userId, request), ErrorCode.PAYMENT_APPROVAL_FAILED);
        assertThat(paymentStatus(request.orderId())).isEqualTo(PaymentStatus.FAILED);
        assertThat(shop.getOrder(userId, order.orderId()).status()).isEqualTo(StoreOrderStatus.EXPIRED);
        expiration.expireOrder(order.orderId());
        shop.cancelOrder(userId, order.orderId());
        assertThat(stock(popcornId)).isEqualTo(10);
    }

    private StoreOrderCreateRequest request(int quantity) {
        return new StoreOrderCreateRequest(theaterId, List.of(new OrderItemRequest(popcornId, quantity)));
    }

    private StoreOrderResponse create(int quantity) { return shop.createOrder(userId, request(quantity)); }

    private PaymentConfirmRequest prepare(Long orderId, String remoteStatus) {
        return stub(preparation.prepareStoreOrder(userId, new StoreOrderPaymentPrepareRequest(orderId)), remoteStatus);
    }

    private PaymentConfirmRequest stub(PaymentPrepareResponse prepared, String remoteStatus) {
        var request = new PaymentConfirmRequest("test_" + UUID.randomUUID(), prepared.orderId(), prepared.amount());
        when(toss.confirm(eq(new TossPaymentConfirmRequest(request.paymentKey(), request.orderId(), request.amount())), anyString()))
                .thenReturn(remote(request, remoteStatus));
        return request;
    }

    private TossPaymentConfirmResponse remote(PaymentConfirmRequest request, String status) {
        return new TossPaymentConfirmResponse(request.paymentKey(), request.orderId(), request.amount(), "매점 상품 주문",
                status, "카드", status.equals("DONE") ? OffsetDateTime.now().withNano(0) : null);
    }

    private String confirmHttp(PaymentConfirmRequest request) throws Exception {
        return mvc.perform(post("/api/payments/confirm").header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.storeOrderStatus").value("PAID"))
                .andExpect(jsonPath("$.reservationId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
    }

    private void backdate(Long id) {
        jdbc.update("update store_order set expires_at = ? where order_id = ?", LocalDateTime.now().minusMinutes(1), id);
        jdbc.update("update payment set expires_at = ? where target_type = 'STORE_ORDER' and target_id = ?",
                LocalDateTime.now().minusMinutes(1), id);
    }

    private int stock(Long menuId) {
        return jdbc.queryForObject("select stock from store_inventory where theater_id = ? and menu_id = ?",
                Integer.class, theaterId, menuId);
    }

    private PaymentStatus paymentStatus(String orderId) {
        return payments.findAll().stream().filter(p -> p.getOrderId().equals(orderId)).findFirst().orElseThrow().getStatus();
    }

    private boolean buyLastItem(CountDownLatch start, Long buyer) throws InterruptedException {
        start.await();
        try { shop.createOrder(buyer, request(1)); return true; }
        catch (GlobalException e) { assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK); return false; }
    }

    private String bearer(Long id) { return "Bearer " + jwt.createAccessToken(id); }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GlobalException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private Menu menu(String name, int price) {
        jdbc.update("insert into menu(menu_name, menu_price) values (?, ?)", name, price);
        Long id = jdbc.queryForObject("select menu_id from menu where menu_name = ?", Long.class, name);
        return entityManager.find(Menu.class, id);
    }

    private <T> T persist(T value) { entityManager.persist(value); return value; }
}
