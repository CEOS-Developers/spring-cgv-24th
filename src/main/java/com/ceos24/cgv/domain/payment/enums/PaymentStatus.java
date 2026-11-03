package com.ceos24.cgv.domain.payment.enums;

public enum PaymentStatus {
	READY,              // 결제 대기
	DONE,               // 결제 완료
	PARTIAL_CANCELED,   // 부분 취소
	CANCELED,           // 전체 취소
	FAILED,             // 결제 실패
	IN_PROGRESS,        // 승인 요청 중 (중복 승인 방지용 선점 상태)
	UNKNOWN             // 토스 결과 불명 (타임아웃·5xx·승인 후 DB 반영 실패) → 재확인 필요
}
