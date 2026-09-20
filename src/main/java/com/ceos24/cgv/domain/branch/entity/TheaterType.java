package com.ceos24.cgv.domain.branch.entity;

// 좌석을 개별 행으로 저장하지 않고 행·열 크기만 보관한다.
// 상영관 종류가 2가지로 고정되고 종류가 같으면 배치가 동일하므로 ENUM이 적합하다.
public enum TheaterType {

    STANDARD("일반관", 8, 10),
    SPECIAL("특별관", 10, 20);

    private final String displayName;
    private final int rowCount;
    private final int colCount;

    TheaterType(String displayName, int rowCount, int colCount) {
        this.displayName = displayName;
        this.rowCount = rowCount;
        this.colCount = colCount;
    }

    public String getDisplayName() { return displayName; }
    public int getRowCount() { return rowCount; }
    public int getColCount() { return colCount; }

    public int getTotalSeatCount() {
        return rowCount * colCount;
    }

    // 좌석 범위 검증은 DB가 해주지 못하므로 도메인에서 책임진다.
    public boolean isValidSeat(int rowNum, int colNum) {
        return rowNum >= 1 && rowNum <= rowCount
                && colNum >= 1 && colNum <= colCount;
    }
}
