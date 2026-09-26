package com.ceos.cgv.domain.auth.security;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.domain.auth.dto.SignupRequest;
import com.ceos.cgv.domain.auth.service.RegistrationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityFlowIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RegistrationService registrationService;
    @Autowired JwtService jwtService;

    @BeforeEach
    void movie() {
        jdbcTemplate.update("""
                INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility)
                VALUES (8842, '공개 영화', '설명', 120, '2026-09-15', 'ALL', 'PUBLIC')
                """);
    }

    @Test
    void 토큰이_없으면_영화_찜은_공통_JSON으로_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/movies/8842/likes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
    }

    @Test
    void H2_콘솔과_문서의_쓰기_경로는_공개_예외가_아니다() throws Exception {
        for (String path : new String[]{"/h2-console/login.do", "/v3/api-docs"}) {
            mockMvc.perform(post(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        }
    }

    @Test
    void 잘못된_서명과_만료된_토큰은_서로_다른_401_코드로_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/movies/8842/likes")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        mockMvc.perform(post("/api/v1/movies/8842/likes")
                        .header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));

        String otherKeyToken = Jwts.builder()
                .issuer("spring-cgv-24th")
                .subject("41")
                .claim("role", "USER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(1800)))
                .signWith(Keys.hmacShaKeyFor("fedcba9876543210fedcba9876543210".getBytes()), Jwts.SIG.HS256)
                .compact();
        mockMvc.perform(post("/api/v1/movies/8842/likes")
                        .header("Authorization", "Bearer " + otherKeyToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void 공개_영화_조회는_잘못된_토큰을_검증하지_않는다() throws Exception {
        String publicResponse = mockMvc.perform(get("/api/v1/movies/8842"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (String authorization : new String[]{"Bearer invalid.jwt.token", "Bearer " + expiredToken()}) {
            String response = mockMvc.perform(get("/api/v1/movies/8842")
                            .header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.movieId").value(8842))
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).isEqualTo(publicResponse);
        }
    }

    @Test
    void 인증_요청_다음에_토큰을_빼면_이전_인증이_남지_않는다() throws Exception {
        Long userId = registrationService.register(new SignupRequest(
                "authuser", "인증 회원", "authuser@example.com", "Password123!")).getId();
        String token = jwtService.issue(userId, UserRole.USER);

        mockMvc.perform(post("/api/v1/movies/8842/likes")
                        .param("userId", userId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/movies/8842/likes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
    }

    @Test
    void 영화_찜은_쿼리의_다른_회원_ID가_아니라_토큰_회원에게_저장한다() throws Exception {
        Long ownerId = registrationService.register(new SignupRequest(
                "realowner", "실제 회원", "realowner@example.com", "Password123!")).getId();
        Long otherId = registrationService.register(new SignupRequest(
                "otheruser", "다른 회원", "otheruser@example.com", "Password123!")).getId();

        mockMvc.perform(post("/api/v1/movies/8842/likes")
                        .param("userId", otherId.toString())
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM movie_likes WHERE movie_id = 8842", Long.class))
                .isEqualTo(ownerId);
        mockMvc.perform(post("/api/v1/movies/8842/likes")
                        .header("Authorization", "Bearer " + jwtService.issue(otherId, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_likes WHERE movie_id = 8842", Integer.class))
                .isEqualTo(2);
    }

    private static String expiredToken() {
        return Jwts.builder()
                .issuer("spring-cgv-24th")
                .subject("41")
                .claim("role", "USER")
                .issuedAt(Date.from(Instant.parse("2020-01-01T00:00:00Z")))
                .expiration(Date.from(Instant.parse("2020-01-01T00:30:00Z")))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")), Jwts.SIG.HS256)
                .compact();
    }
}
