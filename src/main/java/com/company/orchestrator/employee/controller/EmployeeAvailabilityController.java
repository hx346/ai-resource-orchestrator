package com.company.orchestrator.employee.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.employee.dto.AvailabilityUpsertRequest;
import com.company.orchestrator.employee.service.EmployeeAvailabilityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 员工可用性接口 / Employee availability API. */
@Tag(name = "Employee Availability", description = "员工可用性 / Employee availability windows")
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/availability")
@RequiredArgsConstructor
public class EmployeeAvailabilityController {

    private final EmployeeAvailabilityService availabilityService;

    @Operation(summary = "查询员工可用性 / List availability records of an employee")
    @GetMapping
    public Result<List<com.company.orchestrator.employee.entity.EmployeeAvailability>> list(@PathVariable Long employeeId) {
        return Result.ok(availabilityService.listByEmployee(employeeId));
    }

    @Operation(summary = "新增可用性记录 / Add an availability record")
    @PostMapping
    public Result<Long> create(@PathVariable Long employeeId,
                               @Valid @RequestBody AvailabilityUpsertRequest request) {
        return Result.ok(availabilityService.create(employeeId, request));
    }

    @Operation(summary = "删除可用性记录 / Delete an availability record")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long employeeId, @PathVariable Long id) {
        availabilityService.delete(employeeId, id);
        return Result.ok();
    }
}
