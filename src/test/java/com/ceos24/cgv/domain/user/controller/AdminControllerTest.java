package com.ceos24.cgv.domain.user.controller;

import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.security.AuthUser;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest extends ControllerIntegrationTest {

    @Test
    void 관리자_토큰이면_200() throws Exception {
        User admin = persist(TestFixtures.admin("admin01"));

        mockMvc.perform(get("/api/admin/check").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(admin.getId()))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void 일반_사용자_토큰이면_403() throws Exception {
        User user = persist(TestFixtures.user("user01"));

        mockMvc.perform(get("/api/admin/check").with(bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 토큰이_없으면_403이_아니라_401() throws Exception {
        mockMvc.perform(get("/api/admin/check"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
    }

    // 토큰에는 접두사 없는 "ADMIN"이 실리고, 권한 문자열의 "ROLE_"은 Role.getAuthority() 한 곳에서만 붙는다.
    // hasRole("ADMIN")은 내부에서 "ROLE_"을 붙여 같은 문자열을 비교하므로 어느 쪽으로 규칙을 써도 결과가 같다.
    @Test
    void hasRole과_hasAuthority는_같은_권한_문자열을_비교한다() {
        AuthUser admin = new AuthUser(1L, Role.ADMIN);
        AuthUser user = new AuthUser(2L, Role.USER);

        assertThat(admin.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");

        AuthorizationManager<Object> byRole = AuthorityAuthorizationManager.hasRole("ADMIN");
        AuthorizationManager<Object> byAuthority = AuthorityAuthorizationManager.hasAuthority(Role.ADMIN.getAuthority());

        assertThat(granted(byRole, admin)).isTrue();
        assertThat(granted(byAuthority, admin)).isTrue();
        assertThat(granted(byRole, user)).isFalse();
        assertThat(granted(byAuthority, user)).isFalse();
    }

    private boolean granted(AuthorizationManager<Object> manager, AuthUser principal) {
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        return manager.authorize(() -> authentication, new Object()).isGranted();
    }
}
