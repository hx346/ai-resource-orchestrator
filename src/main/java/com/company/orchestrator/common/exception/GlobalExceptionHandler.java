package com.company.orchestrator.common.exception;

import com.company.orchestrator.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
        log.warn("request body / param error: {}", e.getMessage());
        return badRequest(resolve(ErrorCode.BAD_REQUEST, null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return badRequest(resolve(ErrorCode.BAD_REQUEST, null));
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

    private ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.httpStatus())
                .body(Result.fail(ErrorCode.BAD_REQUEST.code(), message));
    }

    private String resolve(ErrorCode errorCode, Object[] args) {
        return messageSource.getMessage(errorCode.messageKey(), args,
                errorCode.messageKey(), LocaleContextHolder.getLocale());
    }
}
