package com.ceos24.cgv.domain.user.dto;

/**
 * @param expiresIn             액세스 토큰 유효 시간(초). 클라이언트가 만료 전에 재발급을 준비할 수 있게 준다.
 * @param refreshToken          재발급·로그아웃 요청 본문에 넣을 원문. 서버는 해시만 가지고 있어 다시 보여줄 수 없다.
 * @param refreshTokenExpiresIn 리프레시 토큰 유효 시간(초). 무작위 문자열이라 토큰 자체에서는 만료를 읽을 수 없다.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshTokenExpiresIn
) {
    private static final String BEARER = "Bearer";

    public static LoginResponse of(String accessToken, long expiresIn,
                                   String refreshToken, long refreshTokenExpiresIn) {
        return new LoginResponse(accessToken, BEARER, expiresIn, refreshToken, refreshTokenExpiresIn);
    }
}
