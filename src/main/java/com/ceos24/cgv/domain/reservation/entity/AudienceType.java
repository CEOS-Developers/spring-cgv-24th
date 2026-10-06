package com.ceos24.cgv.domain.reservation.entity;

// 권종별 가격표를 테이블로 두지 않는다. 값이 고정된 소수이고 기준가 대비 할인율만 다르다.
// 기준가는 회차가 갖고(Screening.price) 권종은 거기서 얼마를 깎는지만 안다.
public enum AudienceType {

    ADULT("일반", 0),
    YOUTH("청소년", 20),
    PREFERENTIAL("우대", 50),
    SENIOR("경로", 50);

    private final String displayName;
    private final int discountRate;

    AudienceType(String displayName, int discountRate) {
        this.displayName = displayName;
        this.discountRate = discountRate;
    }

    public String getDisplayName() { return displayName; }

    public int calculatePrice(int basePrice) {
        return basePrice * (100 - discountRate) / 100;
    }
}
