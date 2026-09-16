package com.ceos.cgv.domain.concession.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FoodOrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 데이터베이스에_매점_주문_기본_데이터를_넣는다() {
        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (91, '매점 테스트 사용자', 'food-order-test@example.com')");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (92, '매점 테스트 영화관 1', '서울')");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (93, '매점 테스트 영화관 2', '부산')");
        jdbcTemplate.update("INSERT INTO products (product_id, name, price, description) VALUES (94, '팝콘', 1200, '테스트 상품')");
        jdbcTemplate.update("INSERT INTO inventories (inventory_id, cinema_id, product_id, stock_quantity) VALUES (95, 92, 94, 3)");
        jdbcTemplate.update("INSERT INTO inventories (inventory_id, cinema_id, product_id, stock_quantity) VALUES (96, 93, 94, 5)");
    }

    @Test
    void 존재하지_않는_사용자는_매점_주문을_할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/food-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 999999,
                                  "cinemaId": 999999,
                                  "items": [
                                    {"productId": 999999, "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 주문_항목이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/food-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "cinemaId": 1,
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 상품_메뉴는_영화관과_상관없이_공통으로_조회된다() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value(94))
                .andExpect(jsonPath("$.data[0].name").value("팝콘"))
                .andExpect(jsonPath("$.data[0].price").value(1200));
    }

    @Test
    void 상품과_영화관별_초기_재고를_등록한다() throws Exception {
        String productLocation = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "콜라", "price": 900, "description": "음료"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("콜라"))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        long productId = Long.parseLong(productLocation.substring(productLocation.lastIndexOf('/') + 1));

        mockMvc.perform(post("/api/v1/inventories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cinemaId": 92, "productId": %d, "stockQuantity": 4}
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stockQuantity").value(4));

        mockMvc.perform(post("/api/v1/inventories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cinemaId": 92, "productId": %d, "stockQuantity": 4}
                                """.formatted(productId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_INVENTORY"));
    }

    @Test
    void 주문_금액을_서버에서_계산하고_선택한_영화관의_재고만_차감한다() throws Exception {
        String location = mockMvc.perform(post("/api/v1/food-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 91,
                                  "cinemaId": 92,
                                  "totalPrice": 1,
                                  "items": [{"productId": 94, "quantity": 2}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalPrice").value(2400))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        long orderId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(get("/api/v1/food-orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPrice").value(2400))
                .andExpect(jsonPath("$.data.items[0].productName").value("팝콘"));

        Integer selectedCinemaStock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM inventories WHERE cinema_id = 92 AND product_id = 94", Integer.class
        );
        Integer otherCinemaStock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM inventories WHERE cinema_id = 93 AND product_id = 94", Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(selectedCinemaStock).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(otherCinemaStock).isEqualTo(5);
    }

    @Test
    void 재고보다_많은_수량은_주문되지_않고_재고도_그대로다() throws Exception {
        mockMvc.perform(post("/api/v1/food-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 91,
                                  "cinemaId": 92,
                                  "items": [{"productId": 94, "quantity": 4}]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_ENOUGH"));

        Integer remainingStock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM inventories WHERE cinema_id = 92 AND product_id = 94", Integer.class
        );
        Integer savedOrders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM food_orders", Integer.class);
        org.assertj.core.api.Assertions.assertThat(remainingStock).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(savedOrders).isZero();
    }
}
