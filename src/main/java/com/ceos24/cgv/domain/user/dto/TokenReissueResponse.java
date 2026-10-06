package com.ceos24.cgv.domain.user.dto;

/**
 * @param refreshToken          다음 재발급에 쓸 새 원문. 요청에 보낸 토큰은 사용 완료되어 다시 쓸 수 없다.
 * @param refreshTokenExpiresIn 남은 유효 시간(초). 만료 시각은 로그인 때 정해져 순환해도 늘어나지 않는다.
 */
public record TokenReissueResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshTokenExpiresIn
) {
    private static final String BEARER = "Bearer";

    public static TokenReissueResponse of(String accessToken, long expiresIn,
                                          String refreshToken, long refreshTokenExpiresIn) {
        return new TokenReissueResponse(accessToken, BEARER, expiresIn, refreshToken, refreshTokenExpiresIn);
    }
}
