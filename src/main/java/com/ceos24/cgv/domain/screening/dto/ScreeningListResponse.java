package com.ceos24.cgv.domain.screening.dto;

import com.ceos24.cgv.domain.branch.entity.TheaterType;

import java.time.LocalDate;
import java.util.List;

// 화면이 지점 안에서 상영관 종류로 묶어 보여주므로 응답도 같은 모양으로 내린다.
public record ScreeningListResponse(
        LocalDate date,
        List<BranchGroup> branches
) {
    public record BranchGroup(
            Long branchId,
            String branchName,
            List<FormatGroup> formats
    ) {}

    public record FormatGroup(
            TheaterType theaterType,
            String theaterTypeName,
            int screeningCount,
            List<ScreeningResponse> screenings
    ) {
        public static FormatGroup of(TheaterType theaterType, List<ScreeningResponse> screenings) {
            return new FormatGroup(theaterType, theaterType.getDisplayName(),
                    screenings.size(), screenings);
        }
    }
}
