package com.ceos24.cgv.domain.branch.entity;

import static com.ceos24.cgv.domain.branch.entity.TheaterCategory.GENERAL;
import static com.ceos24.cgv.domain.branch.entity.TheaterCategory.SPECIAL;

// 좌석을 개별 행으로 저장하지 않고 행·열 크기만 보관한다.
// 종류가 같으면 배치가 동일하고 종류 수가 소수로 고정되므로 ENUM이 적합하다.
// 선언 순서가 곧 지점 목록의 특별관 라벨 순서다.
public enum TheaterType {

    STANDARD("일반관", GENERAL, 8, 10),
    IMAX("IMAX", SPECIAL, 12, 22),
    FOUR_DX("4DX", SPECIAL, 10, 16),
    SCREEN_X("SCREENX", SPECIAL, 10, 20);

    private final String displayName;
    private final TheaterCategory category;
    private final int rowCount;
    private final int colCount;

    TheaterType(String displayName, TheaterCategory category, int rowCount, int colCount) {
        this.displayName = displayName;
        this.category = category;
        this.rowCount = rowCount;
        this.colCount = colCount;
    }

    public String getDisplayName() { return displayName; }
    public TheaterCategory getCategory() { return category; }
    public int getRowCount() { return rowCount; }
    public int getColCount() { return colCount; }

    public boolean isSpecial() {
        return category == SPECIAL;
    }

    public int getTotalSeatCount() {
        return rowCount * colCount;
    }

    // 좌석 범위 검증은 DB가 해주지 못하므로 도메인에서 책임진다.
    public boolean isValidSeat(int rowNum, int colNum) {
        return rowNum >= 1 && rowNum <= rowCount
                && colNum >= 1 && colNum <= colCount;
    }
}
