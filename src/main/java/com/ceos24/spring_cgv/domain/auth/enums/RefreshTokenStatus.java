package com.ceos24.spring_cgv.domain.auth.enums;

public enum RefreshTokenStatus {
    NOT_FOUND, // 키 자체가 없음 -> 로그아웃 또는 만료
    VALID, // 저장된 해시와 일치
    MISMATCH // 키는 있으나 값이 다름 -> 이미 회전된 옛 RT (탈취 의심)
}
