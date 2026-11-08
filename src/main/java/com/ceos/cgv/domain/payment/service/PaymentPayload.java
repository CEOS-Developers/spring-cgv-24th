package com.ceos.cgv.domain.payment.service;

import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentPayload {
    private final PaymentJson paymentJson;

    public String confirm(PaymentConfirmRequest request) {
        if (request == null
                || request.paymentKey() == null
                || request.paymentKey().isBlank()
                || request.paymentKey().length() > 200
                || request.orderId() == null
                || request.orderId().isBlank()
                || request.amount() == null
                || request.amount() <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return hash(paymentJson.write(request));
    }

    public String cancel(Long paymentId, PaymentCancelRequest request) {
        if (request == null
                || request.reason() == null
                || request.reason().isBlank()
                || request.reason().length() > 200)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        List<Long> ids = request.reservedSeatIds();
        if (ids != null
                && (ids.isEmpty()
                        || ids.stream().anyMatch(id -> id == null || id <= 0)
                        || new HashSet<>(ids).size() != ids.size()))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        // FULL is the original intent. Actual seats and amount belong to the operation snapshot.
        return hash(
                paymentJson.write(
                        Arrays.asList(
                                paymentId,
                                ids == null ? "FULL" : ids.stream().sorted().toList(),
                                request.reason())));
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
