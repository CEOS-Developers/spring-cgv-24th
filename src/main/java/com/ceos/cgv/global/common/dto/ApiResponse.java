package com.ceos.cgv.global.common.dto;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.function.Function;

public record ApiResponse<T>(
        int status,
        String message,
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), "요청에 성공하였습니다.", data);
    }

    public static <T, R> ApiResponse<List<R>> success(List<T> values, Function<T, R> mapper) {
        return success(values.stream().map(mapper).toList());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), "생성이 완료되었습니다.", data);
    }
}
