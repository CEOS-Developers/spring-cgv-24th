package com.ceos24.cgv.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// traceId는 관측 필터가 요청마다 MDC에 넣는다. 필터 등록이 빠지거나 로그 패턴에서 빠지면
// 로그만 보고는 알아채기 어려워서, 실제 필터 체인을 태운 요청의 로그 줄로 확인한다.
@SpringBootTest(properties = "logging.level.com.ceos24.cgv.global.exception=debug")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class TraceIdLoggingTest {

    private static final Pattern TRACE_ID = Pattern.compile("\\[([0-9a-f]{32})-[0-9a-f]{16}] .*\\[NotReadable]");

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("요청 중에 남긴 로그에 요청마다 다른 traceId가 붙는다")
    void 요청_중_로그에_요청마다_다른_traceId가_붙는다(CapturedOutput output) throws Exception {
        sendBrokenJson();
        sendBrokenJson();

        List<String> traceIds = TRACE_ID.matcher(output.getOut()).results()
                .map(m -> m.group(1))
                .toList();
        assertThat(traceIds).hasSize(2);
        assertThat(traceIds.get(0)).isNotEqualTo(traceIds.get(1));
    }

    private void sendBrokenJson() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":"))
                .andExpect(status().isBadRequest());
    }
}
