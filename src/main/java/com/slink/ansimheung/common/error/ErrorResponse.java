package com.slink.ansimheung.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 모든 API 의 오류 응답 모양 (노션 "API 공통 규약" 3장).
 * <pre>{ "code": "OUT_OF_AREA", "message": "...", "fields": null }</pre>
 * fields 는 VALIDATION 일 때만 채우고, 그 외에는 null 로 보낸다 (필드를 빼지 않음).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ErrorResponse(String code, String message, Map<String, String> fields) {

    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code.name(), code.defaultMessage(), null);
    }

    public static ErrorResponse of(ErrorCode code, String message, Map<String, String> fields) {
        return new ErrorResponse(code.name(), message == null ? code.defaultMessage() : message, fields);
    }
}
