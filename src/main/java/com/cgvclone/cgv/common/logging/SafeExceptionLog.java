package com.cgvclone.cgv.common.logging;

import org.slf4j.Logger;

public final class SafeExceptionLog {
    private SafeExceptionLog() {
    }

    public static void write(Logger logger, Throwable exception) {
        // 예외 메시지/응답 본문/SQL 바인딩 값은 제외하고 원인 타입과 호출 위치만 보존한다.
        StringBuilder stack = new StringBuilder();
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            stack.append(cause.getClass().getName()).append('\n');
            StackTraceElement[] frames = cause.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 40); i++) {
                stack.append("\tat ").append(frames[i]).append('\n');
            }
        }
        logger.atError().addKeyValue("event", "application.error")
                .addKeyValue("exceptionType", exception.getClass().getName())
                .addKeyValue("stackTrace", stack.toString()).log("Unexpected application error");
    }
}
