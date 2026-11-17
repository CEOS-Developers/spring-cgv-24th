package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.TossWebhookRequest;
import com.ceos24.cgv.global.config.TossProperties;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.global.logging.LogMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final ObjectMapper objectMapper;
    private final TossProperties tossProperties;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentService paymentService;
    private final PaymentTransactionService paymentTransactionService;

    public void handleWebhook(
            String rawBody, String signatureHeader, String transmissionTimeHeader, String secretHeader) {
        log.info("webhook_received transmissionTime={}", transmissionTimeHeader);

        TossWebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, TossWebhookRequest.class);
        } catch (Exception e) {
            // 예외 메시지에 본문 조각이 섞일 수 있어 예외 종류만 남긴다
            log.warn("webhook_payload_invalid type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "웹훅 페이로드 형식이 올바르지 않습니다.");
        }

        verifyWebhookSignature(rawBody, signatureHeader, transmissionTimeHeader, secretHeader, request);

        String paymentKey = request.resolvedPaymentKey();
        String orderId = request.resolvedOrderId();

        if (paymentKey == null || paymentKey.isBlank()) {
            paymentKey = paymentTransactionService.resolvePaymentKeyByOrderId(orderId);
        }

        if (paymentKey == null || paymentKey.isBlank()) {
            log.warn(
                    "webhook_ignored reason=payment_key_missing orderId={} eventType={}", orderId, request.eventType());
            return;
        }

        // 토스 결제 단건 조회 API를 통한 신뢰할 수 있는 최신 상태 교차 검증
        TossPaymentResponse latest;
        try {
            latest = tossPaymentsClient.getPayment(paymentKey);
        } catch (Exception e) {
            log.error(
                    "webhook_inquiry_failed paymentKey={} type={}",
                    LogMasker.mask(paymentKey),
                    e.getClass().getSimpleName());
            throw e;
        }

        if (orderId != null && !orderId.isBlank()) {
            latest.validateIntegrity(orderId, null);
        }

        dispatchWebhookEvent(latest);
    }

    private void dispatchWebhookEvent(TossPaymentResponse latest) {
        String status = latest.status();
        log.info(
                "webhook_apply orderId={} paymentKey={} status={}",
                latest.orderId(),
                LogMasker.mask(latest.paymentKey()),
                status);

        if ("DONE".equals(status)) {
            paymentService.completeApprovedPaymentFromWebhook(latest);
        } else if ("CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status)) {
            paymentTransactionService.applyCancelWebhook(latest);
        } else if ("ABORTED".equals(status) || "EXPIRED".equals(status)) {
            paymentTransactionService.abortPendingPaymentFromWebhook(latest.orderId(), latest.paymentKey());
        } else {
            log.info("webhook_ignored reason=unsupported_status orderId={} status={}", latest.orderId(), status);
        }
    }

    private void verifyWebhookSignature(
            String rawBody,
            String signatureHeader,
            String transmissionTimeHeader,
            String secretHeader,
            TossWebhookRequest request) {
        String configuredSecret = tossProperties.webhookSecret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            // fail-closed: 시크릿 미설정 상태에서는 어떤 웹훅도 수락하지 않는다.
            log.error("webhook_secret_not_configured action=set_env name=TOSS_WEBHOOK_SECRET");
            throw new BusinessException(ErrorCode.WEBHOOK_INVALID_SIGNATURE, "웹훅 시크릿이 서버에 설정되지 않았습니다.");
        }

        // 1. HMAC-SHA256 서명 헤더 검증 (tosspayments-webhook-signature)
        if (signatureHeader != null && !signatureHeader.isBlank()) {
            String time = transmissionTimeHeader != null ? transmissionTimeHeader : "";
            String payloadToSign = rawBody + ":" + time;
            String calculatedSignature = calculateHmacSha256Base64(payloadToSign, configuredSecret);

            boolean signatureMatched = false;
            String[] parts = signatureHeader.split(",");
            for (String part : parts) {
                String token = part.trim();
                // 형식: "v1:<base64>". base64는 ':'를 포함하지 않지만 패딩 '='를 포함하므로 '='로 자르면 안 된다.
                String sigValue = token;
                int colonIdx = token.indexOf(':');
                if (colonIdx >= 0) {
                    sigValue = token.substring(colonIdx + 1).trim();
                }

                if (constantTimeEquals(calculatedSignature, sigValue)) {
                    signatureMatched = true;
                    break;
                }
            }

            if (!signatureMatched) {
                log.warn("webhook_auth_failed reason=hmac_mismatch");
                throw new BusinessException(ErrorCode.WEBHOOK_INVALID_SIGNATURE);
            }
            return;
        }

        // 2. 가상계좌 콜백 또는 커스텀 시크릿 헤더 검증 (X-Webhook-Secret / body.secret)
        if (secretHeader != null && !secretHeader.isBlank()) {
            if (!constantTimeEquals(configuredSecret, secretHeader)) {
                log.warn("webhook_auth_failed reason=secret_header_mismatch");
                throw new BusinessException(ErrorCode.WEBHOOK_INVALID_SIGNATURE);
            }
            return;
        }

        if (request.resolvedSecret() != null && !request.resolvedSecret().isBlank()) {
            if (!constantTimeEquals(configuredSecret, request.resolvedSecret())) {
                log.warn("webhook_auth_failed reason=body_secret_mismatch");
                throw new BusinessException(ErrorCode.WEBHOOK_INVALID_SIGNATURE);
            }
            return;
        }

        log.warn("webhook_auth_failed reason=credentials_missing");
        throw new BusinessException(ErrorCode.WEBHOOK_INVALID_SIGNATURE, "웹훅 인증 헤더 또는 시크릿이 누락되었습니다.");
    }

    private String calculateHmacSha256Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            log.error("webhook_hmac_calculation_failed type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.WEBHOOK_INVALID_SIGNATURE);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
