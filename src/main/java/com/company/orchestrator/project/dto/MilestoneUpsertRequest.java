package com.company.orchestrator.project.dto;

import java.time.LocalDate;

import com.company.orchestrator.project.entity.ProjectMilestone;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 里程碑创建/更新入参 / Milestone create/update request. */
public record MilestoneUpsertRequest(
        @NotBlank @Size(max = 256) String name,
        LocalDate dueDate,
        @NotNull @Min(0) @Max(9999) Integer sortOrder) {

    public static MilestoneUpsertRequest from(ProjectMilestone milestone) {
        return new MilestoneUpsertRequest(milestone.getName(), milestone.getDueDate(), milestone.getSortOrder());
    }
}
