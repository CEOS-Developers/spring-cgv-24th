package com.ceos24.cgv.domain.snack.exception;

import com.ceos24.cgv.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SnackOrderErrorCode implements BaseErrorCode {

    EMPTY_SNACK_ORDER(
            HttpStatus.BAD_REQUEST,
            "SNACK_ORDER4001",
            "구매할 상품이 없습니다."
    ),

    INVALID_SNACK_ITEM(
            HttpStatus.BAD_REQUEST,
            "SNACK_ORDER4002",
            "상품 정보가 올바르지 않습니다."
    ),

    INVALID_SNACK_QUANTITY(
            HttpStatus.BAD_REQUEST,
            "SNACK_ORDER4003",
            "상품 구매 수량은 1개 이상이어야 합니다."
    ),

    DUPLICATE_SNACK_ITEM(
            HttpStatus.BAD_REQUEST,
            "SNACK_ORDER4004",
            "동일한 상품이 주문에 중복으로 포함되어 있습니다."
    ),

    SNACK_STOCK_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SNACK_ORDER4041",
            "해당 영화관에서 판매하지 않는 상품입니다."
    ),

    INSUFFICIENT_SNACK_STOCK(
            HttpStatus.CONFLICT,
            "SNACK_ORDER4091",
            "상품 재고가 부족합니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}