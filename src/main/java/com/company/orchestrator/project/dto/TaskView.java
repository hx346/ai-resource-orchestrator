package com.company.orchestrator.project.dto;

import java.time.LocalDate;

import com.company.orchestrator.project.entity.Task;

/** 任务视图 / Task view. */
public record TaskView(
        Long id,
        Long projectId,
        Long parentId,
        Long milestoneId,
        String name,
        String description,
        Integer priority,
        Integer estimatedHours,
        LocalDate startDate,
        LocalDate endDate,
        String status) {

    public static TaskView from(Task task) {
        return new TaskView(task.getId(), task.getProjectId(), task.getParentId(), task.getMilestoneId(),
                task.getName(), task.getDescription(), task.getPriority(), task.getEstimatedHours(),
                task.getStartDate(), task.getEndDate(), task.getStatus());
    }
}
