package com.ceos24.cgv.domain.user.dto;

// 리프레시 토큰은 돌려주지 않는다. 순환 발급 전이라 클라이언트는 로그인 때 받은 것을 계속 쓴다.
public record TokenReissueResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    private static final String BEARER = "Bearer";

    public static TokenReissueResponse of(String accessToken, long expiresIn) {
        return new TokenReissueResponse(accessToken, BEARER, expiresIn);
    }
}
