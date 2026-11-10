package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.TossConfirmResponse;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorCode;
import com.ceos24.cgv.domain.payment.exception.PaymentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalServiceTest {

    @Mock PaymentTransactionService transactions;
    @Mock TossPaymentClient toss;
    @InjectMocks PaymentApprovalService service;

    private final PaymentConfirmRequest request =
            new PaymentConfirmRequest("new-key", "order-123", 10000);

    @Test
    void rejectedApprovalReturnsPaymentErrorAndLeavesResultForRecovery() {
        given(transactions.prepareApproval(1L, request)).willReturn(true);
        given(toss.confirm(request)).willThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.confirm(1L, request))
                .isInstanceOf(PaymentException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_APPROVAL_REJECTED);

        verify(transactions).markApprovalUnknown(request);
        verify(transactions).prepareApproval(1L, request);
        verifyNoMoreInteractions(transactions);
    }

    @Test
    void networkFailureKeepsApprovalUncertain() {
        given(transactions.prepareApproval(1L, request)).willReturn(true);
        given(toss.confirm(request)).willThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> service.confirm(1L, request))
                .isInstanceOf(PaymentException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_APPROVAL_UNRESOLVED);

        verify(transactions).markApprovalUnknown(request);
    }

    @Test
    void abortedRecoveryPersistsFailureBeforeReturningError() {
        TossConfirmResponse aborted = new TossConfirmResponse(
                "new-key", "order-123", 10000, "ABORTED", null);
        given(toss.getPayment("new-key")).willReturn(aborted);

        assertThatThrownBy(() -> service.recoverApproval(1L, request))
                .isInstanceOf(PaymentException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_APPROVAL_REJECTED);

        verify(transactions).completeFailedApproval(request, aborted);
    }

    @Test
    void doneRecoveryCompletesApproval() {
        TossConfirmResponse done = new TossConfirmResponse(
                "new-key", "order-123", 10000, "DONE", OffsetDateTime.now());
        given(toss.getPayment("new-key")).willReturn(done);
        given(transactions.completeApproval(request, done)).willReturn(done);

        service.recoverApproval(1L, request);

        verify(transactions).completeApproval(request, done);
    }
}
