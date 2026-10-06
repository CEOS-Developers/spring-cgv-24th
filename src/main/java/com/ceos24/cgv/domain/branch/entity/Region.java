package com.ceos24.cgv.domain.branch.entity;

import java.util.Arrays;
import java.util.List;

// 극장 목록의 지역 탭 값. 행정구역과 1:1이 아니라 서비스가 임의로 정한 묶음이므로
// 외부 표준 데이터에 종속되지 않는다. 그래서 테이블이 아닌 ENUM으로 둔다.
// 선언 순서가 곧 탭 노출 순서라 별도의 정렬 컬럼이 필요 없다.
public enum Region {

    SEOUL("서울"),
    GYEONGGI("경기"),
    INCHEON("인천"),
    GANGWON("강원"),
    DAEJEON_CHUNGCHEONG("대전·충청"),
    DAEGU("대구"),
    BUSAN_ULSAN("부산·울산"),
    GYEONGSANG("경상"),
    GWANGJU_JEOLLA("광주·전라"),
    JEJU("제주");

    private final String displayName;

    Region(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    // 표시명은 DB에 없으므로 "지역명 또는 극장명" 검색은 키워드를 먼저 Region으로 바꿔야 한다.
    public static List<Region> searchByKeyword(String keyword) {
        return Arrays.stream(values())
                .filter(region -> region.displayName.contains(keyword))
                .toList();
    }
}
