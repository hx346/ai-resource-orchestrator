package com.company.orchestrator.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 全局业务错误码：messageKey 对应 messages*.properties 中的双语文案。
 * Global business error codes; message keys resolve to bilingual message bundles.
 */
public enum ErrorCode {

    BAD_REQUEST("E40000", "error.bad.request", HttpStatus.BAD_REQUEST),
    ROLE_INVALID("E40001", "auth.role.invalid", HttpStatus.BAD_REQUEST),

    AI_NOT_ENABLED("E50301", "ai.not.enabled", HttpStatus.SERVICE_UNAVAILABLE),
    AI_NO_PROVIDER("E50302", "ai.no.provider", HttpStatus.SERVICE_UNAVAILABLE),
    AI_BUSY("E42901", "ai.busy", HttpStatus.TOO_MANY_REQUESTS),
    AI_TIMEOUT("E50401", "ai.timeout", HttpStatus.GATEWAY_TIMEOUT),
    AI_CANCELLED("E50303", "ai.cancelled", HttpStatus.SERVICE_UNAVAILABLE),
    AI_UPSTREAM_FAILED("E50201", "ai.upstream.failed", HttpStatus.BAD_GATEWAY),

    NOT_FOUND("E40400", "error.not.found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("E40500", "error.method.not.allowed", HttpStatus.METHOD_NOT_ALLOWED),
    EMPLOYEE_NOT_FOUND("E40401", "employee.not.found", HttpStatus.NOT_FOUND),
    DEPARTMENT_NOT_FOUND("E40402", "department.not.found", HttpStatus.NOT_FOUND),
    SKILL_NOT_FOUND("E40403", "skill.not.found", HttpStatus.NOT_FOUND),
    SKILL_CATEGORY_NOT_FOUND("E40404", "skill.category.not.found", HttpStatus.NOT_FOUND),
    PROJECT_NOT_FOUND("E40405", "project.not.found", HttpStatus.NOT_FOUND),
    TASK_NOT_FOUND("E40406", "task.not.found", HttpStatus.NOT_FOUND),
    TASK_DEPENDENCY_NOT_FOUND("E40407", "task.dependency.not.found", HttpStatus.NOT_FOUND),
    TASK_REQUIREMENT_NOT_FOUND("E40410", "task.requirement.not.found", HttpStatus.NOT_FOUND),
    MILESTONE_NOT_FOUND("E40408", "milestone.not.found", HttpStatus.NOT_FOUND),
    AVAILABILITY_NOT_FOUND("E40409", "availability.not.found", HttpStatus.NOT_FOUND),

    DUPLICATE("E40901", "error.duplicate", HttpStatus.CONFLICT),
    SKILL_IN_USE("E40902", "skill.in.use", HttpStatus.CONFLICT),
    DEPARTMENT_NOT_EMPTY("E40903", "department.not.empty", HttpStatus.CONFLICT),

    INVALID_TASK_DEPENDENCY("E42201", "task.dependency.invalid", HttpStatus.UNPROCESSABLE_ENTITY),

    INTERNAL_ERROR("E50000", "error.internal", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String messageKey;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String messageKey, HttpStatus httpStatus) {
        this.code = code;
        this.messageKey = messageKey;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String messageKey() {
        return messageKey;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
