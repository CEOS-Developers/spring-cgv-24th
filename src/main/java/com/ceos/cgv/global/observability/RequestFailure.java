package com.ceos.cgv.global.observability;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public record RequestFailure(String code, String exceptionType) {
    private static final String ATTRIBUTE = RequestFailure.class.getName();
    static final String TRACKED_ATTRIBUTE = ATTRIBUTE + ".tracked";

    public static boolean set(String code, Throwable exception) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && Boolean.TRUE.equals(attributes.getRequest().getAttribute(TRACKED_ATTRIBUTE))) {
            attributes.getRequest().setAttribute(ATTRIBUTE, from(code, exception));
            return true;
        }
        return false;
    }

    public static void set(HttpServletRequest request, Enum<?> code) {
        request.setAttribute(ATTRIBUTE, new RequestFailure(code.name(), null));
    }

    static RequestFailure get(HttpServletRequest request) {
        return (RequestFailure) request.getAttribute(ATTRIBUTE);
    }

    static RequestFailure from(String code, Throwable exception) {
        return new RequestFailure(code, exception.getClass().getSimpleName());
    }
}
