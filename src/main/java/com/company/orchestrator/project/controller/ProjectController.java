package com.company.orchestrator.project.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.PageVO;
import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.project.dto.ProjectUpsertRequest;
import com.company.orchestrator.project.dto.ProjectView;
import com.company.orchestrator.project.service.ProjectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 项目接口 / Project API. */
@Tag(name = "Project", description = "项目管理 / Project management")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "项目分页查询 / Page projects")
    @GetMapping
    public Result<PageVO<ProjectView>> page(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return Result.ok(PageVO.from(projectService.page(pageNum, pageSize, keyword, status))
                .map(ProjectView::from));
    }

    @Operation(summary = "项目详情 / Get project by id")
    @GetMapping("/{id}")
    public Result<ProjectView> get(@PathVariable Long id) {
        return Result.ok(ProjectView.from(projectService.requireExists(id)));
    }

    @Operation(summary = "创建项目 / Create project")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProjectUpsertRequest request) {
        return Result.ok(projectService.create(request));
    }

    @Operation(summary = "更新项目 / Update project")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectUpsertRequest request) {
        projectService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除项目（级联里程碑与任务）/ Delete project with milestones and tasks")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }
}
