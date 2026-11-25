package com.ceos24.cgv.global.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.assertj.core.api.Assertions.assertThat;

// 운영 로그 레벨은 경고가 "사람이 봐야 하는 일"만 가리키도록 정한 결과라, 레벨 자체를 검증한다.
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerLogTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private Level originalLevel;

    @BeforeEach
    void enableDebug() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void restoreLevel() {
        logger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("4xx CustomException은 경고가 아니라 DEBUG로 남긴다")
    void 클라이언트_오류는_DEBUG로_남긴다(CapturedOutput output) {
        handler.handleCustom(new CustomException(ErrorCode.USER_NOT_FOUND));

        assertThat(output.getOut()).contains("DEBUG").contains("USER_NOT_FOUND")
                                   .doesNotContain("WARN");
    }

    @Test
    @DisplayName("5xx 계열 CustomException은 WARN으로 남긴다")
    void 서버측_오류는_WARN으로_남긴다(CapturedOutput output) {
        handler.handleCustom(new CustomException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE));

        assertThat(output.getOut()).contains("WARN").contains("PAYMENT_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("본문을 읽지 못한 요청의 로그에는 예외 메시지(본문 일부)를 남기지 않는다")
    void 본문_파싱_실패_로그에_본문을_남기지_않는다(CapturedOutput output) {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "JSON parse error: \"password\":\"secret-pw\"",
                new UnsupportedOperationException("secret-pw"),
                new MockHttpInputMessage(new byte[0]));

        handler.handleNotReadable(e);

        assertThat(output.getOut()).contains("[NotReadable]").contains("UnsupportedOperationException")
                                   .doesNotContain("secret-pw");
    }
}
