package com.cgvclone.cgv.domain.payment;

import com.cgvclone.cgv.domain.auth.CustomUserDetails;
import com.cgvclone.cgv.domain.purchase.dto.PurchaseResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        tossPaymentClient.clientKey();
        Optional<PurchaseResponse> completed = paymentOrderService.prepare(user.getUserId(), request);
        if (completed.isPresent()) {
            return completed.get();
        }
        TossPayment payment = tossPaymentClient.confirm(request);
        return paymentOrderService.complete(user.getUserId(), request, payment);
    }

    @PostMapping("/{orderId}/cancel")
    public PurchaseResponse cancel(@AuthenticationPrincipal CustomUserDetails user, @PathVariable String orderId) {
        return paymentOrderService.cancel(user.getUserId(), orderId);
    }

    public record Config(
            String clientKey
    ) {
    }
}
