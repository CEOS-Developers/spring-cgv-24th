package com.ceos.cgv.domain.payment.controller;

import com.ceos.cgv.domain.payment.dto.response.PaymentClientConfigResponse;
import com.ceos.cgv.domain.payment.gateway.PaymentGatewayException;
import com.ceos.cgv.domain.payment.infrastructure.toss.config.TossPaymentProperties;
import com.ceos.cgv.global.common.dto.ApiResponse;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-config")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PaymentClientConfigController {
    private final TossPaymentProperties properties;

    @GetMapping
    public ResponseEntity<ApiResponse<PaymentClientConfigResponse>> config() {
        try {
            PaymentClientConfigResponse response = PaymentClientConfigResponse.builder()
                    .clientKey(properties.testClientKey()).currency("KRW").build();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(response));
        } catch (PaymentGatewayException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
