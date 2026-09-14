package com.company.orchestrator.employee.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.employee.dto.DepartmentUpsertRequest;
import com.company.orchestrator.employee.dto.DepartmentView;
import com.company.orchestrator.employee.service.DepartmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 部门接口 / Department API. */
@Tag(name = "Department", description = "部门管理 / Department management")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "部门列表 / List departments")
    @GetMapping
    public Result<List<DepartmentView>> list() {
        return Result.ok(departmentService.listAll().stream()
                .map(DepartmentView::from)
                .toList());
    }

    @Operation(summary = "创建部门 / Create department")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DepartmentUpsertRequest request) {
        return Result.ok(departmentService.create(request));
    }

    @Operation(summary = "更新部门 / Update department")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DepartmentUpsertRequest request) {
        departmentService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除部门 / Delete department")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.ok();
    }
}
