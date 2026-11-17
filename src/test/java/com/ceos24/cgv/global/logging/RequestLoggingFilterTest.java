package com.ceos24.cgv.global.logging;

import com.ceos24.cgv.domain.user.enums.UserRole;
import com.ceos24.cgv.global.security.CustomUserDetails;
import com.ceos24.cgv.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.ceos24.cgv.global.logging.RequestLoggingFilter.TRACE_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 응답_헤더의_traceId와_사용자_id가_액세스_로그에_함께_남는다(CapturedOutput output) throws Exception {
        String token = jwtTokenProvider.createAccessToken(new CustomUserDetails(7L, UserRole.USER));

        String traceId = mockMvc.perform(get("/api/not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(header().string(TRACE_ID_HEADER, matchesPattern("[0-9a-f]{32}")))
                .andReturn().getResponse().getHeader(TRACE_ID_HEADER);

        assertThat(output.getOut().lines())
                .anyMatch(line -> line.contains("[" + traceId + ",7]")
                        && line.contains("HTTP 요청 처리 method=GET uri=/api/not-exist status=404"));
    }

    @Test
    void 요청이_끝나면_MDC를_비운다() throws Exception {
        String token = jwtTokenProvider.createAccessToken(new CustomUserDetails(7L, UserRole.USER));

        mockMvc.perform(get("/api/not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        assertThat(MDC.get(LogContext.TRACE_ID)).isNull();
        assertThat(MDC.get(LogContext.USER_ID)).isNull();
    }

    @Test
    void 액추에이터_요청은_액세스_로그를_남기지_않는다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().doesNotExist(TRACE_ID_HEADER));

        assertThat(output.getOut()).doesNotContain("uri=/actuator/health");
    }
}
