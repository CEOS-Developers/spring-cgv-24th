package com.cgvclone.cgv.domain.movie;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ContentRating {

    ALL("전체관람가"),
    RATE_12("12세 이상 관람가"),
    RATE_15("15세 이상 관람가"),
    RATE_18("청소년 관람불가"),
    RESTRICTED("제한상영가");

    private final String description;
}
