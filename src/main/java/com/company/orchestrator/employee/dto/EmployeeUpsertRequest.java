package com.company.orchestrator.employee.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 员工创建/更新入参 / Employee create/update request. */
public record EmployeeUpsertRequest(
        @NotBlank @Size(max = 64) String employeeNo,
        @NotBlank @Size(max = 128) String name,
        @NotNull Long departmentId,
        @Size(max = 128) String position,
        @Size(max = 128) String location,
        @NotNull @Min(1) @Max(168) Integer weeklyHours,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal defaultCapacity) {
}
