package com.company.orchestrator.skill.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.company.orchestrator.common.enums.SkillSource;
import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.skill.dto.AiSkillAcceptItem;
import com.company.orchestrator.skill.dto.AiSkillExtractRequest;
import com.company.orchestrator.skill.dto.EmployeeSkillUpsertRequest;
import com.company.orchestrator.skill.dto.EmployeeSkillView;
import com.company.orchestrator.skill.service.AiSkillProfileService;
import com.company.orchestrator.skill.service.EmployeeSkillService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 员工技能画像接口 / Employee skill profile API. */
@Tag(name = "Employee Skill", description = "员工技能画像 / Employee skill profiles")
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/skills")
@RequiredArgsConstructor
public class EmployeeSkillController {

    private final EmployeeSkillService employeeSkillService;
    private final AiSkillProfileService aiSkillProfileService;

    @Operation(summary = "查询员工技能画像 / List skills of an employee")
    @GetMapping
    public Result<List<EmployeeSkillView>> list(@PathVariable Long employeeId) {
        return Result.ok(employeeSkillService.listByEmployee(employeeId));
    }

    @Operation(summary = "全量替换员工技能 / Replace all skills of an employee")
    @PutMapping
    public Result<Void> replaceAll(@PathVariable Long employeeId,
                                   @Valid @RequestBody List<@Valid EmployeeSkillUpsertRequest> requests) {
        employeeSkillService.replaceAll(employeeId, requests);
        return Result.ok();
    }

    @Operation(summary = "清空员工技能 / Clear all skills of an employee")
    @DeleteMapping
    public Result<Void> clear(@PathVariable Long employeeId) {
        employeeSkillService.replaceAll(employeeId, List.of());
        return Result.ok();
    }

    @Operation(summary = "AI 从经历文本识别技能草稿（不落库）/ Extract a skill draft from experience text via AI (nothing persisted)")
    @PostMapping("/ai-extract")
    public Result<?> aiExtract(@PathVariable Long employeeId, @Valid @RequestBody AiSkillExtractRequest request) {
        return Result.ok(aiSkillProfileService.extract(employeeId, request));
    }

    @Operation(summary = "上传简历/文档文件（txt/md/docx/pdf）抽取技能草稿 / Upload a resume file and extract a skill draft")
    @PostMapping(value = "/ai-extract-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> aiExtractFile(@PathVariable Long employeeId, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) SkillSource source) {
        return Result.ok(aiSkillProfileService.extractFromFile(employeeId, file, source));
    }

    @Operation(summary = "人工确认草稿写入画像 / Merge human-confirmed draft items into the profile")
    @PostMapping("/ai-accept")
    public Result<Integer> aiAccept(@PathVariable Long employeeId, @Valid @RequestBody List<@Valid AiSkillAcceptItem> items) {
        return Result.ok(aiSkillProfileService.accept(employeeId, items));
    }

    @Operation(summary = "历史任务技能证据 / Skill evidence from active allocations")
    @GetMapping("/evidence")
    public Result<?> evidence(@PathVariable Long employeeId) {
        return Result.ok(aiSkillProfileService.evidence(employeeId));
    }
}
