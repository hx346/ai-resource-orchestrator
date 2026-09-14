package com.company.orchestrator.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 部门创建/更新入参 / Department create/update request. */
public record DepartmentUpsertRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 64) String code,
        Long parentId,
        @Size(max = 512) String description) {
}
