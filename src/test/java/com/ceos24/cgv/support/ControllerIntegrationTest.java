package com.ceos24.cgv.support;

import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.security.jwt.JwtProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

// 보호 API는 토큰을 필터가 풀어 SecurityContext에 넣어야 컨트롤러가 사용자를 안다.
// 그래서 컨트롤러 테스트도 실제 필터 체인을 태운다.
@SpringBootTest
@Transactional
public abstract class ControllerIntegrationTest {

    protected MockMvc mockMvc;

    @Autowired protected EntityManager em;
    @Autowired private WebApplicationContext wac;
    @Autowired private JwtProvider jwtProvider;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    protected <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    protected void flushAndClear() {
        em.flush();
        em.clear();
    }

    // 토큰을 요청이 만들어지는 순간에 발급한다. 테스트가 Clock을 목으로 바꿔 시간을 밀어도
    // 발급 시각과 검증 시각이 같은 시계를 보므로 만료되지 않는다.
    protected RequestPostProcessor bearer(User user) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION,
                    "Bearer " + jwtProvider.createAccessToken(user.getId(), user.getRole()));
            return request;
        };
    }

    // 탈퇴 등으로 DB에서 사라진 사용자. 토큰은 만료 전까지 서명 검증을 통과한다.
    protected User missingUser() {
        User user = TestFixtures.user("missinguser");
        ReflectionTestUtils.setField(user, "id", 9999L);
        return user;
    }
}
