package com.company.orchestrator.common.exception;

/**
 * 业务异常：携带错误码与消息占位参数，由全局异常处理器本地化后返回。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.messageKey());
        this.errorCode = errorCode;
        this.args = args;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args == null ? new Object[0] : args.clone();
    }
}
