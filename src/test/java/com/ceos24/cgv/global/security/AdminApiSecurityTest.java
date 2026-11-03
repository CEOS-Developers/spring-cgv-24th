package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.concession.service.ItemService;
import com.ceos24.cgv.domain.user.enums.UserRole;
import com.ceos24.cgv.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiSecurityTest {

    private static final String ITEM_BODY = "{\"name\":\"팝콘\",\"price\":6000}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ItemService itemService;

    private String bearer(UserRole role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(new CustomUserDetails(1L, role));
    }

    @Test
    void 토큰이_없으면_관리자_API는_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/items").contentType(MediaType.APPLICATION_JSON).content(ITEM_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 일반_사용자는_관리자_API에서_403을_반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ITEM_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_관리자_API를_호출할_수_있다() throws Exception {
        when(itemService.create(any())).thenReturn(1L);

        mockMvc.perform(post("/api/admin/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ITEM_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void 토큰이_없으면_사용자_API는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void 공개_조회_API는_토큰_없이_호출할_수_있다() throws Exception {
        mockMvc.perform(get("/api/items")).andExpect(status().isOk());
    }
}
