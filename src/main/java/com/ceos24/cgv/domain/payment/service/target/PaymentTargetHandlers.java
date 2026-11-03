package com.ceos24.cgv.domain.payment.service.target;

import com.ceos24.cgv.domain.payment.entity.PaymentTargetType;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentTargetHandlers {
    private final Map<PaymentTargetType, PaymentTargetHandler> handlers;

    public PaymentTargetHandlers(List<PaymentTargetHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(PaymentTargetHandler::type, Function.identity()));
    }

    public PaymentTargetHandler get(PaymentTargetType type) {
        PaymentTargetHandler handler = handlers.get(type);
        if (handler == null) {
            // 업무 규칙이 등록되지 않은 주문은 PG 호출을 시작하지 않는다.
            throw new BusinessException(ErrorCode.PAYMENT_TARGET_NOT_SUPPORTED);
        }
        return handler;
    }
}
