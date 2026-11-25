package com.ceos.cgv.domain.payment.service.target;

import com.ceos.cgv.domain.payment.enums.PaymentTargetType;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentTargetRegistry {
    private final Map<PaymentTargetType, PaymentTarget> targets;

    public PaymentTargetRegistry(List<PaymentTarget> targets) {
        Map<PaymentTargetType, PaymentTarget> byType = new EnumMap<>(PaymentTargetType.class);
        for (PaymentTarget target : targets) {
            PaymentTargetType type = target.targetType();
            if (byType.putIfAbsent(type, target) != null) {
                throw new IllegalStateException("Multiple payment targets registered for " + type);
            }
        }
        this.targets = Map.copyOf(byType);
    }

    public PaymentTarget get(PaymentTargetType type) {
        PaymentTarget target = targets.get(type);
        if (target == null) {
            throw new IllegalStateException("No payment target registered for " + type);
        }
        return target;
    }
}
