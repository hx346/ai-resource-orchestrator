package com.company.orchestrator.project.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 任务创建/更新入参 / Task create/update request. */
public record TaskUpsertRequest(
        Long parentId,
        Long milestoneId,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 8192) String description,
        @Min(1) @Max(5) Integer priority,
        @Min(0) Integer estimatedHours,
        LocalDate startDate,
        LocalDate endDate) {
}
