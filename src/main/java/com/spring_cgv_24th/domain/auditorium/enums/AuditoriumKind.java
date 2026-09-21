package com.spring_cgv_24th.domain.auditorium.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditoriumKind {
    GENERAL("일반관", AuditoriumCategory.GENERAL),
    IMAX("IMAX", AuditoriumCategory.SPECIAL),
    FOUR_DX("4DX", AuditoriumCategory.SPECIAL);

    private final String displayName;
    private final AuditoriumCategory category;
}
