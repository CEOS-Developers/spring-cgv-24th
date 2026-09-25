package com.ceos24.cgv.domain.user.entity;

public enum Role {
    USER,
    ADMIN;

    // hasRole("ADMIN")은 내부에서 "ROLE_ADMIN"과 비교한다. 접두사를 여러 곳에서 붙이면
    // 한 곳만 빠져도 권한 검사가 조용히 실패하므로 여기서만 만든다.
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
