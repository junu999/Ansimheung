package com.slink.ansimheung.common.error;

import java.util.Map;

/**
 * 서비스 코드에서 던지면 GlobalExceptionHandler 가 공통 오류 응답으로 바꿔준다.
 *
 * <pre>
 * throw new ApiException(ErrorCode.EMAIL_DUP);
 * throw new ApiException(ErrorCode.LIMIT, "오늘은 더 제보할 수 없어요 (하루 10건)");
 * throw ApiException.field("nickname", "닉네임은 한글·영문·숫자 2~12자예요");
 * </pre>
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fields;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, String> fields) {
        super(message == null ? code.defaultMessage() : message);
        this.code = code;
        this.fields = fields;
    }

    /** 입력 칸 하나가 틀렸을 때 (VALIDATION + fields) */
    public static ApiException field(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION, message, Map.of(field, message));
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
