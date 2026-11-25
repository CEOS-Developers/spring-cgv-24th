package com.cgvclone.cgv.domain.payment;

import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.auth.CustomUserDetails;
import com.cgvclone.cgv.domain.purchase.dto.PurchaseResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentOrderService paymentOrderService;
    private final TossPaymentClient tossPaymentClient;

    @GetMapping("/config")
    public Config config() {
        return new Config(tossPaymentClient.clientKey());
    }

    @PostMapping("/confirm")
    public PurchaseResponse confirm(@AuthenticationPrincipal CustomUserDetails user,
                                    @RequestBody PaymentConfirmRequest request) {
        // 검증된 형태의 주문 식별자만 로그 맥락에 추가한다. 요청 종료 시 필터에서 정리한다.
        if (request != null && request.orderId() != null
                && request.orderId().matches("[A-Za-z0-9_=-]{6,64}")) {
            MDC.put("orderId", request.orderId());
        }
        String stage = "prepare";
        try {
            tossPaymentClient.clientKey();
            Optional<PurchaseResponse> completed = paymentOrderService.prepare(user.getUserId(), request);
            if (completed.isPresent()) {
                log.atInfo().addKeyValue("event", "payment.confirmation.reused")
                        .addKeyValue("status", completed.get().status()).log("Existing payment result returned");
                return completed.get();
            }
            // prepare/complete는 별도 서비스 프록시를 통해 호출하므로 반환 시 커밋까지 완료된다.
            log.atInfo().addKeyValue("event", "payment.preparation.completed")
                    .addKeyValue("status", "CONFIRMING").addKeyValue("amount", request.amount())
                    .log("Payment ready for confirmation");
            stage = "provider";
            TossPayment payment = tossPaymentClient.confirm(request);
            stage = "complete";
            PurchaseResponse result = paymentOrderService.complete(user.getUserId(), request, payment);
            log.atInfo().addKeyValue("event", "payment.order.updated")
                    .addKeyValue("status", result.status()).addKeyValue("amount", result.totalPrice())
                    .log("Payment result committed");
            return result;
        } catch (RuntimeException exception) {
            boolean expected = exception instanceof GlobalException global
                    && global.getErrorCode().getStatus().is4xxClientError();
            log.atLevel(expected ? Level.WARN : Level.ERROR)
                    .addKeyValue("event", stage.equals("complete")
                            ? "payment.order.update_failed" : "payment.confirmation.failed")
                    .addKeyValue("stage", stage)
                    .addKeyValue("outcome", stage.equals("provider") ? "unknown" : "failed")
                    .addKeyValue("errorCode", exception instanceof GlobalException global
                            ? global.getErrorCode().name() : "INTERNAL_SERVER_ERROR")
                    .log("Payment confirmation could not be completed");
            throw exception;
        }
    }

    @PostMapping("/{orderId}/cancel")
    public PurchaseResponse cancel(@AuthenticationPrincipal CustomUserDetails user, @PathVariable String orderId) {
        if (orderId.matches("[A-Za-z0-9_=-]{6,64}")) {
            MDC.put("orderId", orderId);
        }
        PurchaseResponse result = paymentOrderService.cancel(user.getUserId(), orderId);
        log.atInfo().addKeyValue("event", "payment.order.cancelled")
                .addKeyValue("status", result.status()).log("Pending order cancellation committed");
        return result;
    }

    public record Config(
            String clientKey
    ) {
    }
}
