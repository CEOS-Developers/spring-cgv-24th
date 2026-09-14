package com.ceos24.cgv.global.apiPayload;

import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonPropertyOrder({"isSuccess", "code", "message", "data"})
@AllArgsConstructor
@Getter
public class ApiResponse<T> {

    private Boolean isSuccess;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> onSuccess(T data) {
        return new ApiResponse<>(true, SuccessStatus.OK.getCode(), SuccessStatus.OK.getMessage(), data);
    }

    public static <T> ApiResponse<T> onSuccess(SuccessStatus status, T data) {
        return new ApiResponse<>(true, status.getCode(), status.getMessage(), data);
    }

    public static <T> ApiResponse<T> onFailure(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data);
    }
}
