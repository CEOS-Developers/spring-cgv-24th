package com.ceos24.cgv.domain.branch.dto;

import com.ceos24.cgv.domain.branch.entity.Region;

// 극장 선택 화면 좌측의 지역 탭. 극장이 없는 지역도 0으로 내려 탭 구성이 흔들리지 않게 한다.
public record RegionResponse(
        Region region,
        String regionName,
        long branchCount
) {
    public static RegionResponse of(Region region, long branchCount) {
        return new RegionResponse(region, region.getDisplayName(), branchCount);
    }
}
