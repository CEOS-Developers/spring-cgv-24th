package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.support.TestFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ControllerIntegrationTest의 MockMvc는 Security 필터 체인을 거치지 않는다.
// 필터를 태워야 "security 의존성을 넣어도 기존 API가 열려 있다"를 확인할 수 있다.
@SpringBootTest
@Transactional
class SecurityConfigTest {

    @Autowired WebApplicationContext wac;
    @Autowired EntityManager em;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    @Test
    void 인증_없이_기존_조회_API를_호출할_수_있다() throws Exception {
        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk());
    }

    @Test
    void CSRF_토큰_없이_쓰기_API를_호출할_수_있다() throws Exception {
        User user = TestFixtures.user("securitytest");
        Branch branch = TestFixtures.branch("강남점");
        em.persist(user);
        em.persist(branch);

        mockMvc.perform(post("/api/branches/{id}/likes", branch.getId())
                        .param("userId", user.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void 세션을_만들지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/movies"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
