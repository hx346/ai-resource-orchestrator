package com.company.orchestrator.employee.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.employee.dto.EmployeeUpsertRequest;
import com.company.orchestrator.employee.entity.Employee;
import com.company.orchestrator.employee.entity.EmployeeAvailability;
import com.company.orchestrator.employee.mapper.EmployeeAvailabilityMapper;
import com.company.orchestrator.employee.mapper.EmployeeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 员工管理 / Employee management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final EmployeeAvailabilityMapper availabilityMapper;
    private final DepartmentService departmentService;

    public IPage<Employee> page(long pageNum, long pageSize, String keyword, Long departmentId) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Employee::getName, keyword)
                        .or()
                        .like(Employee::getEmployeeNo, keyword))
                .eq(departmentId != null, Employee::getDepartmentId, departmentId)
                .orderByAsc(Employee::getId);
        return employeeMapper.selectPage(Page.of(pageNum, pageSize), wrapper);
    }

    public List<Employee> listAll() {
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>().orderByAsc(Employee::getId));
    }

    public Employee requireExists(Long employeeId) {
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND, employeeId);
        }
        return employee;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(EmployeeUpsertRequest request) {
        departmentService.requireExists(request.departmentId());
        requireEmployeeNoUnique(request.employeeNo(), null);

        Employee employee = new Employee();
        apply(employee, request);
        employee.setStatus(Employee.STATUS_ACTIVE);
        employeeMapper.insert(employee);
        log.info("employee created, id={}, employeeNo={}", employee.getId(), employee.getEmployeeNo());
        return employee.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EmployeeUpsertRequest request) {
        Employee employee = requireExists(id);
        departmentService.requireExists(request.departmentId());
        requireEmployeeNoUnique(request.employeeNo(), id);

        apply(employee, request);
        employeeMapper.updateById(employee);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExists(id);
        availabilityMapper.delete(new LambdaQueryWrapper<EmployeeAvailability>()
                .eq(EmployeeAvailability::getEmployeeId, id));
        employeeMapper.deleteById(id);
        log.info("employee deleted, id={}", id);
    }

    private void requireEmployeeNoUnique(String employeeNo, Long excludeId) {
        Employee existing = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmployeeNo, employeeNo)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCode.DUPLICATE, employeeNo);
        }
    }

    private void apply(Employee employee, EmployeeUpsertRequest request) {
        employee.setEmployeeNo(request.employeeNo());
        employee.setName(request.name());
        employee.setDepartmentId(request.departmentId());
        employee.setPosition(request.position());
        employee.setLocation(request.location());
        employee.setWeeklyHours(request.weeklyHours());
        employee.setDefaultCapacity(request.defaultCapacity());
    }
}
