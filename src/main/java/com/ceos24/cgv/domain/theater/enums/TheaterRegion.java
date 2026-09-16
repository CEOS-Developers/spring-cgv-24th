package com.ceos24.cgv.domain.theater.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TheaterRegion {
    SEOUL("서울"),
    GYEONGGI("경기"),
    INCHEON("인천"),
    GANGWON("강원"),
    DAEJEON_CHUNGCHEONG("대전/충청"),
    DAEGU("대구"),
    BUSAN_ULSAN("부산/울산"),
    GYEONGSANG("경상"),
    GWANGJU_JEOLLA("광주/전라"),
    JEJU("제주");

    private final String description;
}
