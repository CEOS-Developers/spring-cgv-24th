package com.spring_cgv_24th.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spring_cgv_24th.domain.favorite.repository.MovieFavoriteRepository;
import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.enums.MemberRole;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.domain.movie.entity.Movie;
import com.spring_cgv_24th.domain.movie.repository.MovieRepository;
import com.spring_cgv_24th.global.jwt.JwtProperties;
import com.spring_cgv_24th.global.jwt.JwtProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationFlowIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private MovieFavoriteRepository movieFavoriteRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JwtProperties jwtProperties;

    // 정상 로그인은 토큰을 발급하고, 없는 계정과 틀린 비밀번호는 같은 오류 본문을 반환한다.
    @Test
    void loginSucceedsAndBothCredentialFailuresHaveSameResponse() throws Exception {
        Account account = signUp();

        String token = login(account.email());
        assertThat(jwtProvider.parseAccessToken(token).memberId()).isEqualTo(account.memberId());
        assertThat(jwtProvider.parseAccessToken(token).role()).isEqualTo(MemberRole.USER);

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(account.email(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH401"))
                .andReturn().getResponse().getContentAsString();

        String missingAccount = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH401"))
                .andReturn().getResponse().getContentAsString();

        assertThat(missingAccount).isEqualTo(wrongPassword).doesNotContain("accessToken");
    }

    // 공개 API는 토큰 없이 열리고, 직전 요청의 인증은 다음 보호 API 요청에 남지 않는다.
    @Test
    void publicApiWorksWithoutTokenAndProtectedApiDoesNotRetainPreviousAuthentication() throws Exception {
        Account account = signUp();
        String token = login(account.email());

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/movies/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFailure(mockMvc.perform(get("/api/movies/favorites")), 401, "TOKEN_NOT_EXIST401");
    }

    // 만료·변조·다른 키로 서명한 토큰과 잘못된 인증 헤더의 401 응답을 확인한다.
    @Test
    void expiredTamperedAndWrongKeyTokensAreRejectedWithCommonJson() throws Exception {
        Instant now = Instant.now();
        SecretKey configuredKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        String expired = signedToken(configuredKey,
                now.minus(Duration.ofMinutes(30)), now.minus(Duration.ofMinutes(15)));
        String wrongKey = signedToken(Jwts.SIG.HS256.key().build(),
                now, now.plus(Duration.ofMinutes(15)));
        String valid = jwtProvider.createAccessToken(1L, MemberRole.USER);
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'a' ? "b" : "a") + parts[2].substring(1);
        String tampered = String.join(".", parts);

        assertFailure(mockMvc.perform(get("/api/admin/check")
                .header(HttpHeaders.AUTHORIZATION, bearer(expired))), 401, "TOKEN_EXPIRED401");
        assertFailure(mockMvc.perform(get("/api/admin/check")
                .header(HttpHeaders.AUTHORIZATION, bearer(tampered))), 401, "TOKEN_INVALID401");
        assertFailure(mockMvc.perform(get("/api/admin/check")
                .header(HttpHeaders.AUTHORIZATION, bearer(wrongKey))), 401, "TOKEN_INVALID401");
        assertFailure(mockMvc.perform(get("/api/admin/check")
                .header(HttpHeaders.AUTHORIZATION, "Basic abc")), 401, "TOKEN_INVALID401");
        assertFailure(mockMvc.perform(get("/api/movies")
                .header(HttpHeaders.AUTHORIZATION, bearer(tampered))), 401, "TOKEN_INVALID401");
    }

    // 실제 로그인 토큰으로 USER의 관리자 접근은 거부하고 ADMIN의 접근은 허용한다.
    @Test
    void userIsDeniedButLoggedInAdminCanAccessAdminApi() throws Exception {
        Account user = signUp();
        String adminEmail = uniqueEmail();
        memberRepository.saveAndFlush(Member.builder()
                .email(adminEmail)
                .name("테스트 관리자")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(MemberRole.ADMIN)
                .build());

        assertFailure(mockMvc.perform(get("/api/admin/check")
                .header(HttpHeaders.AUTHORIZATION, bearer(login(user.email())))),
                403, "ACCESS_DENIED403");

        mockMvc.perform(get("/api/admin/check")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(adminEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // 요청에 소유자 ID를 끼워 넣어도 다른 사용자의 찜은 조회·삭제할 수 없고 데이터도 유지된다.
    @Test
    void anotherUserCannotDeleteOwnersFavoriteOrReadTheirList() throws Exception {
        Account owner = signUp();
        Account other = signUp();
        Movie movie = movieRepository.saveAndFlush(Movie.builder()
                .title("테스트 영화 " + UUID.randomUUID())
                .durationMinutes((short) 90)
                .ageRating("ALL")
                .build());
        String ownerToken = login(owner.email());
        String otherToken = login(other.email());

        mockMvc.perform(post("/api/movies/{movieId}/favorites", movie.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isCreated());

        assertFailure(mockMvc.perform(delete("/api/movies/{movieId}/favorites", movie.getId())
                        .param("memberId", owner.memberId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))),
                404, "MOVIE_FAVORITE404");

        assertThat(movieFavoriteRepository.existsByMember_IdAndMovie_Id(
                owner.memberId(), movie.getId())).isTrue();
        assertThat(movieFavoriteRepository.existsByMember_IdAndMovie_Id(
                other.memberId(), movie.getId())).isFalse();

        mockMvc.perform(get("/api/movies/favorites")
                        .param("memberId", owner.memberId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/movies/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    private Account signUp() throws Exception {
        String email = uniqueEmail();
        String body = "{\"email\":\"%s\",\"name\":\"테스트 회원\",\"password\":\"%s\"}"
                .formatted(email, PASSWORD);
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        long memberId = jsonMapper.readTree(response).path("data").path("memberId").asLong();
        return new Account(memberId, email);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).path("data").path("accessToken").asString();
    }

    private String signedToken(SecretKey key, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .audience().add(jwtProperties.audience()).and()
                .subject("1")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("role", MemberRole.USER.name())
                .claim("tokenType", "ACCESS")
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String uniqueEmail() {
        return "jwt-test-" + UUID.randomUUID() + "@example.com";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void assertFailure(ResultActions result, int httpStatus, String code) throws Exception {
        result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private record Account(Long memberId, String email) {
    }
}
