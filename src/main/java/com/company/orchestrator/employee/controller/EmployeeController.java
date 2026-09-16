package com.company.orchestrator.employee.controller;

import com.company.orchestrator.common.result.PageVO;
import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.employee.dto.EmployeeStatusRequest;
import com.company.orchestrator.employee.dto.EmployeeUpsertRequest;
import com.company.orchestrator.employee.dto.EmployeeView;
import com.company.orchestrator.employee.entity.Employee;
import com.company.orchestrator.employee.service.EmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 员工接口 / Employee API. */
@Tag(name = "Employee", description = "员工管理 / Employee management")
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "员工分页查询（关键字/部门过滤）/ Page employees by keyword or department")
    @GetMapping
    public Result<PageVO<EmployeeView>> page(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long departmentId) {
        return Result.ok(PageVO.from(employeeService.page(pageNum, pageSize, keyword, departmentId))
                .map(EmployeeView::from));
    }

    @Operation(summary = "员工详情 / Get employee by id")
    @GetMapping("/{id}")
    public Result<EmployeeView> get(@PathVariable Long id) {
        return Result.ok(EmployeeView.from(employeeService.requireExists(id)));
    }

    @Operation(summary = "创建员工 / Create employee")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody EmployeeUpsertRequest request) {
        return Result.ok(employeeService.create(request));
    }

    @Operation(summary = "更新员工 / Update employee")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EmployeeUpsertRequest request) {
        employeeService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "员工状态流转（停用/休假/回归在职）/ Change employee status (offboarding, leave, return)")
    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody EmployeeStatusRequest request) {
        employeeService.changeStatus(id, request.status());
        return Result.ok();
    }

    @Operation(summary = "删除员工（连同可用性记录）/ Delete employee with availability records")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return Result.ok();
    }
}
