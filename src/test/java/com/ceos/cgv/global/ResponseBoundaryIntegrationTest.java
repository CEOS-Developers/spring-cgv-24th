package com.ceos.cgv.global;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class ResponseBoundaryIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    // 테스트 자체에도 트랜잭션을 두지 않아 서비스 반환 후의 DTO 직렬화를 검증한다.
    @Test
    void OSIV_없이_상영_예매_주문_생성과_조회를_완료한다() throws Exception {
        jdbc.update("INSERT INTO users(user_id,name,email) VALUES(6101,'응답 회원','response@example.test')");
        jdbc.update("INSERT INTO cinemas(cinema_id,name,address) VALUES(6102,'응답 영화관','서울')");
        jdbc.update("INSERT INTO screens(screen_id,cinema_id,screen_type,row_count,seats_per_row) VALUES(6103,6102,'GENERAL',1,2)");
        jdbc.update("INSERT INTO movies(movie_id,title,description,running_time,release_date,age_rating,visibility) VALUES(6104,'응답 영화','설명',120,'2030-01-01','ALL','PUBLIC')");
        jdbc.update("INSERT INTO products(product_id,name,price,description) VALUES(6105,'팝콘',1200,'설명')");
        jdbc.update("INSERT INTO inventories(inventory_id,cinema_id,product_id,stock_quantity) VALUES(6106,6102,6105,5)");
        String admin = "Bearer " + jwt.issue(6101L, UserRole.ADMIN);
        String user = "Bearer " + jwt.issue(6101L, UserRole.USER);

        String screeningBody = mvc.perform(post("/api/v1/screenings").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":6104,\"screenId\":6103,\"startAt\":\"2030-01-01T12:00:00\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.screenType").value("GENERAL"))
                .andReturn().getResponse().getContentAsString();
        Number screeningId = JsonPath.read(screeningBody, "$.data.screeningId");
        mvc.perform(get("/api/v1/movies/6104/screenings"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].screenId").value(6103));

        String reservationLocation = mvc.perform(post("/api/v1/reservations").header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"screeningId\":%d,\"seats\":[{\"seatRow\":\"A\",\"seatNumber\":1}]}".formatted(screeningId.longValue())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.seats[0].seatRow").value("A"))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(get(reservationLocation).header("Authorization", user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RESERVED"));

        String orderLocation = mvc.perform(post("/api/v1/food-orders").header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cinemaId\":6102,\"items\":[{\"productId\":6105,\"quantity\":2}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalPrice").value(2400))
                .andReturn().getResponse().getHeader("Location");
        jdbc.update("UPDATE products SET price=6000 WHERE product_id=6105");
        mvc.perform(get(orderLocation).header("Authorization", user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalPrice").value(2400))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(1200))
                .andExpect(jsonPath("$.data.items[0].productName").value("팝콘"));
    }
}
