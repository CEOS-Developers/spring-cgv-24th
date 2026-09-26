package com.ceos.cgv.global.security;

import com.ceos.cgv.domain.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminRegistrationAccessIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtService jwtService;

    @Test
    void 상영관_상영일정_상품_재고_등록은_관리자만_가능하다() throws Exception {
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (8830,'관리 영화관','서울')");
        jdbcTemplate.update("""
                INSERT INTO screens (screen_id,cinema_id,screen_type,row_count,seats_per_row)
                VALUES (8831,8830,'GENERAL',10,12)
                """);
        jdbcTemplate.update("""
                INSERT INTO movies (movie_id,title,description,running_time,release_date,age_rating,visibility)
                VALUES (8832,'관리 영화','설명',120,'2026-09-15','ALL','PUBLIC')
                """);
        jdbcTemplate.update("INSERT INTO products (product_id,name,price,description) VALUES (8833,'기존 상품',1000,'설명')");

        String[][] requests = {
                {"/api/v1/screens", """
                        {"cinemaId":8830,"screenType":"GENERAL","rowCount":10,"seatsPerRow":12}
                        """},
                {"/api/v1/screenings", """
                        {"movieId":8832,"screenId":8831,"startAt":"2026-09-26T12:30:00"}
                        """},
                {"/api/v1/products", """
                        {"name":"새 상품","price":900,"description":"음료"}
                        """},
                {"/api/v1/inventories", """
                        {"cinemaId":8830,"productId":8833,"stockQuantity":4}
                        """}
        };
        int[] before = counts();
        for (String[] request : requests) {
            mockMvc.perform(post(request[0])
                            .contentType(MediaType.APPLICATION_JSON).content(request[1]))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
            mockMvc.perform(post(request[0])
                            .header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.USER))
                            .contentType(MediaType.APPLICATION_JSON).content(request[1]))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertThat(counts()).containsExactly(before);
    }

    private int[] counts() {
        return new int[]{
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM screens", Integer.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM screenings", Integer.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventories", Integer.class)
        };
    }
}
