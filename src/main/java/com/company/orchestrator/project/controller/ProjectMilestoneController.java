package com.company.orchestrator.project.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.project.dto.MilestoneUpsertRequest;
import com.company.orchestrator.project.service.ProjectMilestoneService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 里程碑接口 / Milestone API. */
@Tag(name = "Milestone", description = "项目里程碑 / Project milestones")
@RestController
@RequiredArgsConstructor
public class ProjectMilestoneController {

    private final ProjectMilestoneService milestoneService;

    @Operation(summary = "查询项目里程碑 / List milestones of a project")
    @GetMapping("/api/v1/projects/{projectId}/milestones")
    public Result<List<MilestoneUpsertRequest>> list(@PathVariable Long projectId) {
        return Result.ok(milestoneService.listByProject(projectId).stream()
                .map(MilestoneUpsertRequest::from)
                .toList());
    }

    @Operation(summary = "创建里程碑 / Create milestone")
    @PostMapping("/api/v1/projects/{projectId}/milestones")
    public Result<Long> create(@PathVariable Long projectId,
                               @Valid @RequestBody MilestoneUpsertRequest request) {
        return Result.ok(milestoneService.create(projectId, request));
    }

    @Operation(summary = "更新里程碑 / Update milestone")
    @PutMapping("/api/v1/milestones/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MilestoneUpsertRequest request) {
        milestoneService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除里程碑 / Delete milestone")
    @DeleteMapping("/api/v1/milestones/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        milestoneService.delete(id);
        return Result.ok();
    }
}
