package com.company.orchestrator.common.exception;

import com.company.orchestrator.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：业务异常按 ErrorCode 映射 HTTP 状态与本地化文案（zh-CN / en-US）。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        String message = resolve(e.getErrorCode(), e.getArgs());
        log.warn("business error, code={}, message={}", e.getErrorCode().code(), message);
        return ResponseEntity.status(e.getErrorCode().httpStatus())
                .body(Result.fail(e.getErrorCode().code(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String detail = fieldError == null ? "" : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        log.warn("validation error, {}", detail);
        return badRequest(resolve(ErrorCode.BAD_REQUEST, new Object[] {detail}));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleUnreadable(Exception e) {
        // 解析错误原文（类名/JSON 片段）只进日志，不回传客户端 / details stay in logs, not in the response
        log.warn("request body / param error: {}", e.getMessage());
        return badRequest(messageSource.getMessage("error.request.unreadable", null,
                "error.request.unreadable", LocaleContextHolder.getLocale()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return notFound(resolve(ErrorCode.NOT_FOUND, new Object[] {e.getMessage()}));
    }

    /** 方法不匹配：405 + Allow 头 / wrong verb: 405 with Allow header. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("method not allowed: {}", e.getMessage());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus());
        if (e.getSupportedHttpMethods() != null)
            builder = builder.allow(e.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        return builder.body(Result.fail(ErrorCode.METHOD_NOT_ALLOWED.code(),
                resolve(ErrorCode.METHOD_NOT_ALLOWED, new Object[] {e.getMethod()})));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("duplicate key: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.DUPLICATE.httpStatus())
                .body(Result.fail(ErrorCode.DUPLICATE.code(), resolve(ErrorCode.DUPLICATE, null)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(Result.fail(ErrorCode.INTERNAL_ERROR.code(), resolve(ErrorCode.INTERNAL_ERROR, null)));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleIntegrity(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("data integrity violation: {}", e.getMessage());
        return ResponseEntity.status(409).body(Result.fail("CONFLICT",
                messageSource.getMessage("error.data.conflict", null, "error.data.conflict",
                        LocaleContextHolder.getLocale())));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleStatus(org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Result.fail("REQUEST_FAILED", e.getReason()));
    }

    private ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.httpStatus())
                .body(Result.fail(ErrorCode.BAD_REQUEST.code(), message));
    }

    private ResponseEntity<Result<Void>> notFound(String message) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.httpStatus())
                .body(Result.fail(ErrorCode.NOT_FOUND.code(), message));
    }

    private String resolve(ErrorCode errorCode, Object[] args) {
        return messageSource.getMessage(errorCode.messageKey(), args,
                errorCode.messageKey(), LocaleContextHolder.getLocale());
    }
}
