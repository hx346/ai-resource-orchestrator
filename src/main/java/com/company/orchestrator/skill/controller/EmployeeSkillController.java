package com.company.orchestrator.skill.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.skill.dto.EmployeeSkillUpsertRequest;
import com.company.orchestrator.skill.dto.EmployeeSkillView;
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
}
