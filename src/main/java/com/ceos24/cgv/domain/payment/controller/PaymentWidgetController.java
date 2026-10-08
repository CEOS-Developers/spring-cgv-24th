package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.dto.PaymentWidgetConfigResponse;
import com.ceos24.cgv.global.config.TossPaymentsProperties;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 클라이언트 키는 브라우저 소스에 어차피 들어가는 값이라 공개한다. 프런트엔드에 키를 따로 심지 않고
// 서버의 .env 하나에서 짝(클라이언트 키·시크릿 키)을 함께 관리하려고 서버가 내려준다.
@Tag(name = "결제", description = "결제위젯 설정")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentWidgetController {

    private final TossPaymentsProperties tossPaymentsProperties;

    @Operation(summary = "결제위젯 설정 — SDK 초기화용 클라이언트 키")
    @GetMapping("/widget-config")
    public ApiResponse<PaymentWidgetConfigResponse> widgetConfig() {
        return ApiResponse.success(new PaymentWidgetConfigResponse(tossPaymentsProperties.clientKey()));
    }
}
