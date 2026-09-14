package com.company.orchestrator.project.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.project.dto.TaskUpsertRequest;
import com.company.orchestrator.project.dto.TaskView;
import com.company.orchestrator.project.service.TaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 任务接口 / Task API. */
@Tag(name = "Task", description = "任务管理 / Task management")
@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "查询项目任务列表（WBS）/ List tasks of a project")
    @GetMapping("/api/v1/projects/{projectId}/tasks")
    public Result<List<TaskView>> listByProject(@PathVariable Long projectId) {
        return Result.ok(taskService.listByProject(projectId).stream()
                .map(TaskView::from)
                .toList());
    }

    @Operation(summary = "创建任务 / Create task")
    @PostMapping("/api/v1/projects/{projectId}/tasks")
    public Result<Long> create(@PathVariable Long projectId, @Valid @RequestBody TaskUpsertRequest request) {
        return Result.ok(taskService.create(projectId, request));
    }

    @Operation(summary = "更新任务 / Update task")
    @PutMapping("/api/v1/tasks/{taskId}")
    public Result<Void> update(@PathVariable Long taskId, @Valid @RequestBody TaskUpsertRequest request) {
        taskService.update(taskId, request);
        return Result.ok();
    }

    @Operation(summary = "删除任务（连同依赖与技能需求）/ Delete task with dependencies and requirements")
    @DeleteMapping("/api/v1/tasks/{taskId}")
    public Result<Void> delete(@PathVariable Long taskId) {
        taskService.delete(taskId);
        return Result.ok();
    }
}
