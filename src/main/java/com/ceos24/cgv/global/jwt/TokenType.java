package com.ceos24.cgv.global.jwt;

public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
