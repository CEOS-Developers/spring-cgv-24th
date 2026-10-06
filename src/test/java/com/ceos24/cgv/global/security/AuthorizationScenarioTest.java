package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import com.ceos24.cgv.support.AuthScenarioTest;
import com.ceos24.cgv.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 인증은 통과한 요청이 "무엇을 할 수 있는지"를 본다. 역할(관리자 경로)과 소유(남의 예매),
// 그리고 역할이 가입 요청으로 정해질 수 없는지를 확인한다.
class AuthorizationScenarioTest extends AuthScenarioTest {

    private static final String ADMIN_API = "/api/admin/check";

    @Autowired ReservationRepository reservationRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;

    @Test
    @DisplayName("일반 사용자가 관리자 API를 호출하면 403 ACCESS_DENIED 공통 JSON을 받는다")
    void 일반_사용자가_관리자_API를_호출하면_403_ACCESS_DENIED() throws Exception {
        signup("scenario01");
        String token = login("scenario01");

        mockMvc.perform(get(ADMIN_API).with(bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("관리자가 로그인해 받은 토큰으로 관리자 API를 호출하면 정상 처리된다")
    void 관리자가_관리자_API를_호출하면_정상_처리된다() throws Exception {
        User admin = persistAdmin("admin01");
        String token = login("admin01");

        mockMvc.perform(get(ADMIN_API).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(admin.getId()))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("사용자 A의 토큰으로 사용자 B의 예매를 취소하면 거부되고, DB의 B 예매 상태는 바뀌지 않는다")
    void 다른_사용자의_예매를_취소하면_거부되고_DB의_예매_상태는_그대로다() throws Exception {
        // 취소 기한(상영 20분 전)에 걸리지 않도록 실제 시각 기준 내일 회차를 둔다
        Screening screening = persistScreening(LocalDateTime.now().plusDays(1));
        signup("ownerb01");
        signup("attackera1");
        String ownerToken = login("ownerb01");
        String attackerToken = login("attackera1");

        Long reservationId = reserveAndPay(ownerToken, screening.getId());
        flushAndClear();

        mockMvc.perform(delete("/api/reservations/{id}", reservationId).with(bearer(attackerToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        // 응답만으로는 "거부했지만 변경은 이미 반영됨"을 구분할 수 없다. 1차 캐시를 비우고 DB에서 다시 읽는다
        flushAndClear();

        Reservation reloaded = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reloaded.getCancelledAt()).isNull();
        assertThat(reloaded.getSeats()).hasSize(2).allMatch(ReservationSeat::isOccupied);

        // 거부 이유가 소유권이었음을 확인한다. 기한·상태 때문이었다면 주인도 취소하지 못한다
        mockMvc.perform(delete("/api/reservations/{id}", reservationId).with(bearer(ownerToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회원가입 본문에 role: ADMIN을 넣어도 USER로 생성되고, 그 계정으로는 관리자 API가 403이다")
    void 회원가입_본문에_role_ADMIN을_넣어도_USER로_생성된다() throws Exception {
        String response = signupRequest("escalate01", ",\"role\":\"ADMIN\"")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long userId = ((Number) JsonPath.read(response, "$.data.userId")).longValue();
        flushAndClear();

        assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo(Role.USER);

        String token = login("escalate01");
        assertThat(jwtProvider.parse(token).role()).isEqualTo(Role.USER);
        mockMvc.perform(get(ADMIN_API).with(bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private Screening persistScreening(LocalDateTime startAt) {
        Branch branch = persist(TestFixtures.branch("강남점"));
        Theater theater = persist(TestFixtures.theater(branch, TheaterType.STANDARD, "1관"));
        Movie movie = persist(TestFixtures.movie("범죄도시4"));
        return persist(TestFixtures.screening(theater, movie, startAt, 14000));
    }

    private Long reserveAndPay(String token, Long screeningId) throws Exception {
        String body = """
                {"screeningId":%d,"seats":[
                  {"rowNum":1,"colNum":1,"audienceType":"ADULT"},
                  {"rowNum":1,"colNum":2,"audienceType":"ADULT"}]}
                """.formatted(screeningId);
        String response = mockMvc.perform(post("/api/reservations").with(bearer(token))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(response, "$.data.id")).longValue();

        mockMvc.perform(post("/api/reservations/{id}/payment", id).with(bearer(token))
                        .contentType("application/json")
                        .content("{\"result\":\"SUCCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
        return id;
    }
}
