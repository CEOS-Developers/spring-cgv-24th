package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.dto.TheaterInfo;
import java.util.List;

public record GetTheaterResponse(List<TheaterInfo> theaters) {}
