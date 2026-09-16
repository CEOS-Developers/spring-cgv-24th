package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.dto.SeatInfo;
import java.util.List;

public record GetSeatResponse(Long screeningId, List<SeatInfo> seats) {}
