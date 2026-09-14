package com.company.orchestrator.project.dto;

import com.company.orchestrator.common.enums.DependencyType;
import com.company.orchestrator.project.entity.TaskDependency;

import jakarta.validation.constraints.NotNull;

/** 任务依赖创建入参（当前任务为后继任务）/ Task dependency request (current task is the successor). */
public record TaskDependencyRequest(
        @NotNull Long predecessorTaskId,
        DependencyType dependencyType) {

    public static TaskDependencyRequest from(TaskDependency dependency) {
        return new TaskDependencyRequest(dependency.getPredecessorTaskId(), dependency.getDependencyType());
    }
}
