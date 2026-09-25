package com.ceos24.cgv.domain.user.dto;

/**
 * @param expiresIn 토큰 유효 시간(초). 클라이언트가 만료 전에 재로그인을 유도할 수 있게 준다.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    private static final String BEARER = "Bearer";

    public static LoginResponse of(String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, BEARER, expiresIn);
    }
}
