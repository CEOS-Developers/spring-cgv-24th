package com.ceos24.cgv.domain.screening.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 시간대 경계를 이 enum 한 곳에만 둔다. 컨트롤러·서비스에 시각 비교 분기를 흩뿌리지 않는다.
// "전체" 탭은 값으로 두지 않는다. 시·종 시각이 없는 값이 섞이면 startOn/endOn이 의미를 잃어서,
// 파라미터를 비우는 것으로 표현한다.
public enum TimeSlot {

    MORNING("오전", 0, 12),
    AFTERNOON("오후", 12, 18),
    EVENING("18시 이후", 18, 23),
    LATE_NIGHT("심야", 23, 24);

    private final String displayName;
    private final int startHour;
    private final int endHourExclusive;

    TimeSlot(String displayName, int startHour, int endHourExclusive) {
        this.displayName = displayName;
        this.startHour = startHour;
        this.endHourExclusive = endHourExclusive;
    }

    public String getDisplayName() { return displayName; }

    public LocalDateTime startOn(LocalDate date) {
        return date.atStartOfDay().plusHours(startHour);
    }

    // 심야는 해당 날짜의 23~24시만 본다. 자정을 넘긴 회차는 다음 날짜 탭에 잡힌다.
    public LocalDateTime endOn(LocalDate date) {
        return date.atStartOfDay().plusHours(endHourExclusive);
    }
}
