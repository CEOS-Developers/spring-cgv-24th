package com.ceos24.cgv.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

//부분 취소 DTO

public record PaymentPartialCancelRequest(

        //같은 취소 요청을 재전송할때 유지할 id
        @NotNull
        UUID requestId,

        //취소할 ReservationSeat id
        @NotEmpty
        List<@NotNull @Positive Long> reservationSeatIds,

        //취소 사유
        @NotBlank
        @Size(max = 200)
        String cancelReason
) {}