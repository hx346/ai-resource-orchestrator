package com.company.orchestrator.common.result;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * 统一响应包装。code 为业务码（OK 表示成功），message 已按 Accept-Language 本地化，
 * traceId 贯穿单次请求便于排查。
 * Unified response: business code, localized message, payload and traceId.
 */
public record Result<T>(String code, String message, T data, String traceId) {

    public static final String CODE_OK = "OK";

    public static <T> Result<T> ok(T data) {
        return new Result<>(CODE_OK, "ok", data, currentTraceId());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String code, String message) {
        return new Result<>(code, message, null, currentTraceId());
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
