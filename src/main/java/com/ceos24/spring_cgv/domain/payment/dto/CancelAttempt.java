package com.ceos24.spring_cgv.domain.payment.dto;

/***
 * 토스에 보내기 전에 기록한 취소 시도. 별도 트랜잭션에서 만들어져 준영속 상태가 되므로
 * 엔티티를 그대로 들고 나오지 않고 식별자와 멱등성 키만 전달한다.
 */
public record CancelAttempt(Long id, String idempotencyKey) {
}
