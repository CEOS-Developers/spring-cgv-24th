package com.ceos24.cgv.global.logging;

import java.util.UUID;

public final class LogContext {

    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";

    private LogContext() {
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
