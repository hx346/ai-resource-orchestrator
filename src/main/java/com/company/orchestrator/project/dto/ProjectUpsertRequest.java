package com.company.orchestrator.project.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 项目创建/更新入参 / Project create/update request. */
public record ProjectUpsertRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 8192) String description,
        @Min(1) @Max(5) Integer priority,
        LocalDate startDate,
        LocalDate endDate,
        Long managerId,
        @Size(max = 128) String location) {
}
