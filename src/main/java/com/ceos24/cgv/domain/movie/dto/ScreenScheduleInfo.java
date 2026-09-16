package com.ceos24.cgv.domain.movie.dto;

import java.util.List;

public record ScreenScheduleInfo(Long screenId, String screenName, List<ScheduleTimeInfo> schedules) {}
