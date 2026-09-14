package com.company.orchestrator.employee.dto;

import com.company.orchestrator.employee.entity.Department;

/** 部门视图 / Department view. */
public record DepartmentView(
        Long id,
        Long parentId,
        String name,
        String code,
        String description,
        String status) {

    public static DepartmentView from(Department department) {
        return new DepartmentView(department.getId(), department.getParentId(), department.getName(),
                department.getCode(), department.getDescription(), department.getStatus());
    }
}
