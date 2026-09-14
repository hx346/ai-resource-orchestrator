package com.company.orchestrator.employee.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.employee.dto.DepartmentUpsertRequest;
import com.company.orchestrator.employee.entity.Department;
import com.company.orchestrator.employee.entity.Employee;
import com.company.orchestrator.employee.mapper.DepartmentMapper;
import com.company.orchestrator.employee.mapper.EmployeeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 部门管理 / Department management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;

    public List<Department> listAll() {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .orderByAsc(Department::getId));
    }

    public Department requireExists(Long departmentId) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND, departmentId);
        }
        return department;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(DepartmentUpsertRequest request) {
        Department department = new Department();
        apply(department, request);
        department.setStatus(Department.STATUS_ACTIVE);
        departmentMapper.insert(department);
        log.info("department created, id={}, name={}", department.getId(), department.getName());
        return department.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DepartmentUpsertRequest request) {
        Department department = requireExists(id);
        apply(department, request);
        departmentMapper.updateById(department);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExists(id);
        long childCount = departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getParentId, id));
        long employeeCount = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getDepartmentId, id));
        if (childCount > 0 || employeeCount > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_EMPTY, id);
        }
        departmentMapper.deleteById(id);
    }

    private void apply(Department department, DepartmentUpsertRequest request) {
        if (request.parentId() != null && !request.parentId().equals(department.getId())) {
            requireExists(request.parentId());
        }
        department.setParentId(request.parentId());
        department.setName(request.name());
        department.setCode(request.code());
        department.setDescription(request.description());
    }
}
