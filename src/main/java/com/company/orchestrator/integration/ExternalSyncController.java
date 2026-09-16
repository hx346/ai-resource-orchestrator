package com.company.orchestrator.integration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 外部项目同步接口（Jira / 禅道 / GitLab，单向导入）/ External project sync API. */
@Tag(name = "Sync", description = "外部项目同步：Jira / 禅道 / GitLab 单向导入 / External project sync (one-way import)")
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class ExternalSyncController {

    private final SyncService service;

    public record ImportRequest(@NotBlank String externalId) {}
    public record RefreshRequest(@NotNull Long projectId) {}

    @Operation(summary = "列出外部项目 / List remote projects")
    @GetMapping("/{source}/projects")
    public Result<?> projects(@PathVariable String source, @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(source, keyword));
    }

    @Operation(summary = "导入外部项目为 ARO 项目 / Import a remote project")
    @PostMapping("/{source}/import")
    public Result<?> importProject(@PathVariable String source, @Valid @RequestBody ImportRequest request) {
        return Result.ok(service.importProject(source, request.externalId()));
    }

    @Operation(summary = "增量刷新已导入项目（有生效分配时拒绝）/ Refresh an imported project")
    @PostMapping("/{source}/refresh")
    public Result<?> refresh(@PathVariable String source, @Valid @RequestBody RefreshRequest request) {
        return Result.ok(service.refresh(source, request.projectId()));
    }

    @Operation(summary = "查看项目的同步映射 / Mapping detail of an imported project")
    @GetMapping("/{source}/links")
    public Result<?> links(@PathVariable String source, @RequestParam long projectId) {
        return Result.ok(service.links(source, projectId));
    }
}
