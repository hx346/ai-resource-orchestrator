package com.company.orchestrator.employee.dto;

import jakarta.validation.constraints.NotBlank;

/** 员工状态流转请求 / Employee status transition request. */
public record EmployeeStatusRequest(@NotBlank String status) {
}
