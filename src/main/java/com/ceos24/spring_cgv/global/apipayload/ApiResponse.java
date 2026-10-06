package com.ceos24.spring_cgv.global.apipayload;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import com.ceos24.spring_cgv.global.apipayload.code.BaseSuccessCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private final Boolean isSuccess;

    @JsonProperty("code")
    private final String code;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("result")
    private final T result;

    // 결과 데이터가 있는 성공 응답용 오버로딩 메서드
    public static <T> ApiResponse<T> onSuccess(BaseSuccessCode code, T result){
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), result);
    }

    // 결과 데이터가 없는 성공 응답용 오버로딩 메서드
    public static ApiResponse<Void> onSuccess(BaseSuccessCode code){
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), null);
    }

    // 결과 데이터가 있는 실패 응답용 오버로딩 메서드
    public static <T> ApiResponse<T> onFailure(BaseErrorCode code, T result){
        return new ApiResponse<>(false, code.getCode(), code.getMessage(), result);
    }

    // 결과 데이터가 없는 실패 응답용 오버로딩 메서드
    public static ApiResponse<Void> onFailure(BaseErrorCode code){
        return new ApiResponse<>(false, code.getCode(), code.getMessage(), null);
    }
}
