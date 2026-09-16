package com.ceos24.cgv.domain.movie.dto;

import java.time.LocalDateTime;

public record ScheduleTimeInfo(Long screeningId, LocalDateTime startTime, LocalDateTime endTime, Long availableSeats) {}
