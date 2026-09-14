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
import com.company.orchestrator.project.dto.TaskSkillRequirementRequest;
import com.company.orchestrator.project.service.TaskSkillRequirementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 任务技能需求接口 / Task skill requirement API. */
@Tag(name = "Task Skill Requirement", description = "任务技能需求 / Task skill requirements")
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/skill-requirements")
@RequiredArgsConstructor
public class TaskSkillRequirementController {

    private final TaskSkillRequirementService requirementService;

    @Operation(summary = "查询任务技能需求 / List skill requirements of a task")
    @GetMapping
    public Result<List<com.company.orchestrator.project.entity.TaskSkillRequirement>> list(@PathVariable Long taskId) {
        return Result.ok(requirementService.listByTask(taskId));
    }

    @Operation(summary = "新增任务技能需求 / Add a skill requirement")
    @PostMapping
    public Result<Long> create(@PathVariable Long taskId, @Valid @RequestBody TaskSkillRequirementRequest request) {
        return Result.ok(requirementService.create(taskId, request));
    }

    @Operation(summary = "删除任务技能需求 / Delete a skill requirement")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long id) {
        requirementService.delete(taskId, id);
        return Result.ok();
    }
}
