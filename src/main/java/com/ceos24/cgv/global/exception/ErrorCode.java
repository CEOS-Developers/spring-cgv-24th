package com.ceos24.cgv.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    FORBIDDEN_ERROR(409, "허용되지 않는 요청입니다."),

    // Member
    MEMBER_NOT_FOUND(404, "존재하지 않는 회원입니다."),

    // Auth
    AUTH_LOGIN_ID_ALREADY_EXISTS(409, "이미 존재하는 로그인 ID 입니다."),
    AUTH_EMAIL_ALREADY_EXISTS(409, "이미 사용중인 EMAIL입니다."),

    // Store / Order
    STORE_NOT_FOUND(404, "존재하지 않는 매장입니다."),
    MENU_NOT_FOUND(404, "해당 매장에 존재하지 않는 메뉴입니다."),
    OUT_OF_STOCK(400, "재고가 부족합니다."),
    
    // Reservation / Theater / FavoriteTheater
    THEATER_NOT_FOUND(404, "존재하지 않는 영화관입니다."),
    RESERVATION_NOT_FOUND(404, "존재하지 않는 예매이거나 권한이 없습니다."),
    SEAT_NOT_FOUND(404, "존재하지 않는 좌석입니다."),
    SCREENING_NOT_FOUND(404, "존재하지 않는 상영 일정입니다."),
    SCREEN_NOT_FOUND(404, "존재하지 않는 상영관입니다."),
    FAVORITE_THEATER_ALREADY_EXIST(409, "이미 찜한 영화관입니다."),
    FAVORITE_THEATER_NOT_FOUND(404, "찜한 영화관이 존재하지 않습니다."),
    
    // Movie / FavoriteMovie
    MOVIE_NOT_FOUND(404, "존재하지 않는 영화입니다."),
    FAVORITE_MOVIE_ALREADY_EXIST(409, "이미 찜한 영화입니다."),
    FAVORITE_MOVIE_NOT_FOUND(404, "찜한 영화가 존재하지 않습니다.");

    private final int status;
    private final String message;
}
