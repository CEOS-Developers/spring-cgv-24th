package com.spring_cgv_24th.domain.member.enums;

public enum MemberRole {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
