package com.company.orchestrator.project.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.project.dto.TaskDependencyRequest;
import com.company.orchestrator.project.service.TaskDependencyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 任务依赖接口 / Task dependency API. */
@Tag(name = "Task Dependency", description = "任务依赖 / Task dependencies")
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/dependencies")
@RequiredArgsConstructor
public class TaskDependencyController {

    private final TaskDependencyService dependencyService;

    @Operation(summary = "查询任务前置依赖 / List predecessors of a task")
    @GetMapping
    public Result<List<TaskDependencyRequest>> list(@PathVariable Long taskId) {
        return Result.ok(dependencyService.listByTask(taskId).stream()
                .map(TaskDependencyRequest::from)
                .toList());
    }

    @Operation(summary = "新增任务依赖 / Add a predecessor dependency")
    @PostMapping
    public Result<Long> create(@PathVariable Long taskId, @Valid @RequestBody TaskDependencyRequest request) {
        return Result.ok(dependencyService.create(taskId, request));
    }

    @Operation(summary = "删除任务依赖 / Delete a dependency")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long id) {
        dependencyService.delete(id);
        return Result.ok();
    }
}
