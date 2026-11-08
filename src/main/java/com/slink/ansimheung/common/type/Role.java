package com.slink.ansimheung.common.type;

/** 회원 역할. Spring Security 권한 이름은 "ROLE_" + name() */
public enum Role {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
