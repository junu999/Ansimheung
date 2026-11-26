package com.slink.ansimheung.auth.dto;

import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.common.type.Role;

/** 로그인 응답 · GET /api/me = static/mock/me.json */
public record MeResponse(Long id, String nickname, Role role) {

    public static MeResponse from(LoginUser user) {
        return new MeResponse(user.id(), user.nickname(), user.role());
    }
}
