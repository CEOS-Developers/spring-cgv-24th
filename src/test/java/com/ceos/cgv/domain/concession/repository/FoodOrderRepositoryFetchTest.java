package com.ceos.cgv.domain.concession.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureTestDatabase
class FoodOrderRepositoryFetchTest {

    @Autowired
    private FoodOrderRepository foodOrderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 주문_조회_테스트_데이터를_넣는다() {
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id = 191");
        jdbcTemplate.update("DELETE FROM food_orders WHERE order_id = 191");
        jdbcTemplate.update("DELETE FROM inventories WHERE inventory_id = 195");
        jdbcTemplate.update("DELETE FROM products WHERE product_id = 193");
        jdbcTemplate.update("DELETE FROM cinemas WHERE cinema_id = 192");
        jdbcTemplate.update("DELETE FROM users WHERE user_id = 191");

        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (191, '주문 조회 사용자', 'fetch-test@example.com')");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (192, '주문 조회 영화관', '서울')");
        jdbcTemplate.update("INSERT INTO products (product_id, name, price, description) VALUES (193, '팝콘', 1200, '테스트 상품')");
        jdbcTemplate.update("INSERT INTO inventories (inventory_id, cinema_id, product_id, stock_quantity) VALUES (195, 192, 193, 3)");
        jdbcTemplate.update("INSERT INTO food_orders (order_id, user_id, cinema_id, total_price, created_at) VALUES (191, 191, 192, 1200, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO order_items (order_item_id, order_id, product_id, quantity, unit_price) VALUES (194, 191, 193, 1, 1200)");
    }

    @Test
    void 주문과_주문항목을_조회한_뒤_상품명까지_접근할_수_있다() {
        var order = foodOrderRepository.findWithItemsById(191L).orElseThrow();

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getProduct().getName()).isEqualTo("팝콘");
    }
}
