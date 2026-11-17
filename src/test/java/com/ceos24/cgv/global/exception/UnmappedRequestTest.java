package com.ceos24.cgv.global.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class UnmappedRequestTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 없는_경로는_서버_오류가_아닌_404로_응답한다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/wp-admin/setup.php"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON404"));

        assertThat(output.getOut()).doesNotContain("처리되지 않은 예외");
    }

    @Test
    void 지원하지_않는_메서드는_서버_오류가_아닌_405로_응답한다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON405"));

        assertThat(output.getOut()).doesNotContain("처리되지 않은 예외");
    }
}
