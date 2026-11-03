package com.ceos24.cgv.domain.payment.enums;

public enum PaymentStatus {
	READY,              // 결제 대기
	DONE,               // 결제 완료
	PARTIAL_CANCELED,   // 부분 취소
	CANCELED,           // 전체 취소
	FAILED              // 결제 실패
}
