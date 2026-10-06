package com.ceos24.cgv.domain.branch.entity;

// 상영관 종류는 대분류(일반관/특별관) 아래에 IMAX·4DX 같은 실제 종류가 놓이는 2단 구조다.
// 지점 목록의 특별관 라벨처럼 "특별관인가"만 묻는 곳이 있어 대분류를 값으로 갖는다.
public enum TheaterCategory {

    GENERAL("일반관"),
    SPECIAL("특별관");

    private final String displayName;

    TheaterCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
