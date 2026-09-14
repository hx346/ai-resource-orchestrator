package com.company.orchestrator.employee.dto;

import java.math.BigDecimal;

import com.company.orchestrator.employee.entity.Employee;

/** 员工视图 / Employee view. */
public record EmployeeView(
        Long id,
        String employeeNo,
        String name,
        Long departmentId,
        String position,
        String location,
        String status,
        Integer weeklyHours,
        BigDecimal defaultCapacity) {

    public static EmployeeView from(Employee employee) {
        return new EmployeeView(employee.getId(), employee.getEmployeeNo(), employee.getName(),
                employee.getDepartmentId(), employee.getPosition(), employee.getLocation(),
                employee.getStatus(), employee.getWeeklyHours(), employee.getDefaultCapacity());
    }
}
