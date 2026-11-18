package com.ceos24.cgv.global.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void 반복_실행되는_스케줄_작업은_실행마다_새_traceId를_받는다() {
        List<String> traceIds = new ArrayList<>();
        Runnable task = decorator.decorate(() -> traceIds.add(MDC.get(LogContext.TRACE_ID)));

        task.run();
        task.run();

        assertThat(traceIds).hasSize(2).doesNotContainNull();
        assertThat(traceIds.get(0)).isNotEqualTo(traceIds.get(1));
        assertThat(MDC.get(LogContext.TRACE_ID)).isNull();
    }

    @Test
    void 요청_스레드에서_제출한_작업은_요청의_traceId를_이어받는다() {
        MDC.put(LogContext.TRACE_ID, "request-trace");
        MDC.put(LogContext.USER_ID, "7");
        List<String> captured = new ArrayList<>();
        Runnable task = decorator.decorate(() -> {
            captured.add(MDC.get(LogContext.TRACE_ID));
            captured.add(MDC.get(LogContext.USER_ID));
        });
        MDC.clear();

        task.run();

        assertThat(captured).containsExactly("request-trace", "7");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
