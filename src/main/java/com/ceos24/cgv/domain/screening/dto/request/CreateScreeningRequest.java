package com.ceos24.cgv.domain.screening.dto.request;

import java.time.LocalDateTime;

public record CreateScreeningRequest(Long movieId, Long screenId, LocalDateTime startTime, LocalDateTime endTime) {}
