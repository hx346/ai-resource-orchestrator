package com.company.orchestrator.project.dto;

import java.time.LocalDate;

import com.company.orchestrator.project.entity.Project;

/** 项目视图 / Project view. */
public record ProjectView(
        Long id,
        String name,
        String description,
        Integer priority,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long managerId,
        String location) {

    public static ProjectView from(Project project) {
        return new ProjectView(project.getId(), project.getName(), project.getDescription(),
                project.getPriority(), project.getStatus(), project.getStartDate(),
                project.getEndDate(), project.getManagerId(), project.getLocation());
    }
}
