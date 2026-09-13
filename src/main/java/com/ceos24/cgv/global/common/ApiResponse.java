package com.ceos24.cgv.global.common;

public record ApiResponse<T> (
        boolean success,
        int status,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<T>(true, 200, "성공적으로 처리되었습니다.", data);
    }
    public static <T> ApiResponse<T> error(int status, String message) {
        return new ApiResponse<T>(false, status, message, null);
    }
}
