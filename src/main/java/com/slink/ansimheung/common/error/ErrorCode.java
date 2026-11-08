package com.slink.ansimheung.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 공통 오류 코드 (노션 "API 공통 규약" 3장).
 * message 는 사용자에게 그대로 보여줄 기본 문장 — 상황에 맞는 문장이 있으면 ApiException 에서 바꿔 쓴다.
 */
public enum ErrorCode {

    VALIDATION(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요"),
    OUT_OF_AREA(HttpStatus.BAD_REQUEST, "시범 지역(정왕초 반경 500m) 안에서만 이용할 수 있어요"),
    FAR_FROM_ROAD(HttpStatus.BAD_REQUEST, "가까운 길 위를 눌러주세요"),
    OWN_HAZARD(HttpStatus.BAD_REQUEST, "내가 제보한 지점에는 공감할 수 없어요"),
    FILE_TYPE(HttpStatus.BAD_REQUEST, "JPG 또는 PNG 사진만 올릴 수 있어요"),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    AUTH_FAIL(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 틀립니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요"),
    NO_ROUTE(HttpStatus.NOT_FOUND, "경로를 찾지 못했어요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요"),

    EMAIL_DUP(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"),
    DUP_REPORT(HttpStatus.CONFLICT, "이미 제보한 지점이에요. 공감으로 대신할 수 있어요"),
    STATE_CONFLICT(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 작업이에요"),

    FILE_SIZE(HttpStatus.PAYLOAD_TOO_LARGE, "사진은 5MB까지 올릴 수 있어요"),
    LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 가능한 횟수를 모두 사용했어요"),

    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요"),
    NO_GRAPH(HttpStatus.SERVICE_UNAVAILABLE, "경로 데이터가 아직 준비되지 않았어요");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
