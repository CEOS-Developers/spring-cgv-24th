package com.ceos.cgv.global.security.jwt;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.handler.RestAuthenticationEntryPoint;
import com.ceos.cgv.global.security.principal.CgvUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {
    @Test
    void 검증한_JWT로_비밀번호가_없는_UserDetails를_만든다() throws Exception {
        String key = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        JwtService jwt = new JwtService(key, Clock.systemUTC());
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt,
                new RestAuthenticationEntryPoint(JsonMapper.builder().build()), request -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/movies");
        request.addHeader("Authorization", "Bearer " + jwt.issue(42L, UserRole.ADMIN));
        SecurityContextHolder.clearContext();
        AtomicBoolean forwarded = new AtomicBoolean();
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                forwarded.set(true);
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                assertThat(authentication.isAuthenticated()).isTrue();
                assertThat(authentication.getPrincipal()).isInstanceOf(UserDetails.class)
                        .isInstanceOf(CgvUserDetails.class);
                CgvUserDetails principal = (CgvUserDetails) authentication.getPrincipal();
                assertThat(principal.userId()).isEqualTo(42L);
                assertThat(principal.role()).isEqualTo(UserRole.ADMIN);
                assertThat(principal.getPassword()).isNull();
                assertThat(authentication.getCredentials()).isNull();
                assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
            });
            assertThat(forwarded).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
