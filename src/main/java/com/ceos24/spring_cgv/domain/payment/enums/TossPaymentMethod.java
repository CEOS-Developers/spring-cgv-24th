package com.ceos24.spring_cgv.domain.payment.enums;

import com.ceos24.spring_cgv.domain.payment.exception.PaymentException;
import com.ceos24.spring_cgv.domain.payment.exception.code.PaymentErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum TossPaymentMethod {

    CARD("카드"),
    VIRTUAL_ACCOUNT("가상계좌"),
    EASY_PAY("간편결제"),
    TRANSFER("계좌이체"),
    MOBILE_PHONE("휴대폰"),
    CULTURE_GIFT_CERTIFICATE("문화상품권"),
    BOOK_GIFT_CERTIFICATE("도서문화상품권"),
    GAME_GIFT_CERTIFICATE("게임문화상품권");

    // 토스페이먼츠 결제 승인 응답의 method 필드 값 (한글 문자열로 내려온다)
    private final String description;

    /***
     * 함수 기능: 토스가 내려준 한글 결제 수단 문자열을 enum으로 변환한다.
     * @param description 토스 응답의 method 값 (ex. "카드")
     * @return 대응하는 TossPaymentMethod
     */
    public static TossPaymentMethod from(String description) {

        return Arrays.stream(values())
                .filter(method -> method.description.equals(description))
                .findFirst()
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_METHOD_NOT_SUPPORTED));
    }
}
