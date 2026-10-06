package com.ceos.cgv.domain.reservation.service.result;

import com.ceos.cgv.domain.reservation.dto.SeatHoldResponse;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import java.util.Objects;

public sealed interface HoldTransitionResult {
    record Success(SeatHoldResponse response) implements HoldTransitionResult {
        public Success {
            Objects.requireNonNull(response);
        }
    }

    record Failure(ErrorCode error) implements HoldTransitionResult {
        public Failure {
            Objects.requireNonNull(error);
        }
    }

    static HoldTransitionResult success(SeatHoldResponse response) {
        return new Success(response);
    }

    static HoldTransitionResult failure(ErrorCode error) {
        return new Failure(error);
    }

    // 만료·점유 해제 트랜잭션이 커밋된 다음 조정 서비스에서 호출한다.
    default SeatHoldResponse responseOrThrow() {
        return switch (this) {
            case Success success -> success.response();
            case Failure failure -> throw new BusinessException(failure.error());
        };
    }
}
