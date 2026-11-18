package com.ceos24.cgv.global.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

// 스케줄러와 비동기 실행기에 함께 적용된다. 제출한 스레드의 MDC를 이어받고, traceId가 없으면(@Scheduled) 실행마다 새로 발급한다
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submitterContext = MDC.getCopyOfContextMap();
        return () -> {
            if (submitterContext != null) {
                MDC.setContextMap(submitterContext);
            }
            if (MDC.get(LogContext.TRACE_ID) == null) {
                MDC.put(LogContext.TRACE_ID, LogContext.newTraceId());
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
