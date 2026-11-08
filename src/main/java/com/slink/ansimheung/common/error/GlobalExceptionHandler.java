package com.slink.ansimheung.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 컨트롤러에서 나온 예외를 공통 오류 응답 {code, message, fields} 로 바꾼다.
 * 보안 필터 단계(로그인 안 함·권한 없음·CSRF)의 오류는 SecurityErrorHandler 가 같은 모양으로 처리한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.code(), e.getMessage(), e.fields());
    }

    /** @Valid @RequestBody 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return validation(fields);
    }

    /** @RequestParam·@PathVariable 에 붙인 검증 실패 (Spring 6.1+) */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().stream().findFirst()
                    .ifPresent(error -> fields.putIfAbsent(name == null ? "value" : name, error.getDefaultMessage()));
        });
        return validation(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            fields.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
        });
        return validation(fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return validation(Map.of(e.getParameterName(), "필수 값이에요"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return validation(Map.of(e.getRequestPartName(), "필수 값이에요"));
    }

    /** 타입이 안 맞음 — 예: type=FOO 처럼 없는 enum 값, id=abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validation(Map.of(e.getName(), "값이 올바르지 않아요"));
    }

    /** JSON 문법 오류, 없는 enum 값이 본문에 있음 등 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return respond(ErrorCode.VALIDATION, "요청 형식이 올바르지 않아요", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return respond(ErrorCode.VALIDATION, "지원하지 않는 요청 형식이에요", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException e) {
        return respond(ErrorCode.FILE_SIZE, null, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND, null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, null, null);
    }

    /** @PreAuthorize 등 컨트롤러 안에서 막힌 경우: 비로그인이면 401, 로그인했으면 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated();
        return respond(anonymous ? ErrorCode.UNAUTHORIZED : ErrorCode.FORBIDDEN, null, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return respond(ErrorCode.UNAUTHORIZED, null, null);
    }

    /** 예상 못 한 오류: 사용자에게는 일반 문구, 서버 로그에는 전체 스택 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("처리되지 않은 예외", e);
        return respond(ErrorCode.INTERNAL, null, null);
    }

    private ResponseEntity<ErrorResponse> validation(Map<String, String> fields) {
        String message = fields.values().stream().findFirst().orElse(ErrorCode.VALIDATION.defaultMessage());
        return respond(ErrorCode.VALIDATION, message, fields.isEmpty() ? null : fields);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, Map<String, String> fields) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, message, fields));
    }
}
