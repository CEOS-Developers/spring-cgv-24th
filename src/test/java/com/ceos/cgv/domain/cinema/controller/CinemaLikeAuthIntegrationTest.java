package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.user.dto.SignupRequest;
import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.domain.user.security.JwtService;
import com.ceos.cgv.domain.user.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class CinemaLikeAuthIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RegistrationService registrationService;
    @Autowired JwtService jwtService;

    @Test
    void 영화관_찜도_토큰_회원으로만_저장하고_무토큰은_거절한다() throws Exception {
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (8843,'인증 영화관','서울')");
        Long ownerId = registrationService.register(new SignupRequest(
                "cinemaowner", "주인", "cinemaowner@example.com", "Password123!")).getId();
        Long otherId = registrationService.register(new SignupRequest(
                "cinemaother", "다른 회원", "cinemaother@example.com", "Password123!")).getId();

        mockMvc.perform(post("/api/v1/cinemas/8843/likes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        mockMvc.perform(post("/api/v1/cinemas/8843/likes")
                        .param("userId", otherId.toString())
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM cinema_likes WHERE cinema_id=8843", Long.class))
                .isEqualTo(ownerId);
    }
}
