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
    private final com.company.orchestrator.allocation.ReplanTriggerService replan;

    public IPage<Employee> page(long pageNum, long pageSize, String keyword, Long departmentId) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Employee::getName, keyword)
                        .or()
                        .like(Employee::getEmployeeNo, keyword))
                .eq(departmentId != null, Employee::getDepartmentId, departmentId)
                .orderByAsc(Employee::getId);
        return employeeMapper.selectPage(Page.of(Math.max(1,pageNum), Math.max(1,Math.min(500,pageSize))), wrapper);
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

    private static final java.util.Set<String> STATUSES = java.util.Set.of(
            Employee.STATUS_ACTIVE, Employee.STATUS_ON_LEAVE, Employee.STATUS_INACTIVE);

    /** 状态机（纯函数，便于单测）：INACTIVE 仅可回归 ACTIVE / pure transition map; INACTIVE only returns to ACTIVE. */
    static boolean canTransition(String from, String to) {
        if (to.equals(from)) return false;
        return switch (from) {
            case Employee.STATUS_ACTIVE -> java.util.Set.of(Employee.STATUS_ON_LEAVE, Employee.STATUS_INACTIVE).contains(to);
            case Employee.STATUS_ON_LEAVE -> java.util.Set.of(Employee.STATUS_ACTIVE, Employee.STATUS_INACTIVE).contains(to);
            case Employee.STATUS_INACTIVE -> java.util.Set.of(Employee.STATUS_ACTIVE).contains(to);
            default -> false;
        };
    }

    /**
     * 员工状态流转（离场/休假/回归）：停用或休假即时退出候选（CandidateService 只取 ACTIVE），
     * 并触发其生效分配所在项目的重规划巡检——EMPLOYEE_INACTIVE/容量冲突由巡检口径检出。
     * Offboarding and leave: non-ACTIVE employees drop out of candidacy at once,
     * and an event-driven patrol re-checks the plans that booked them.
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        Employee employee = requireExists(id);
        if (!STATUSES.contains(status)) throw new BusinessException(ErrorCode.BAD_REQUEST,"不支持的员工状态："+status);
        if (!canTransition(employee.getStatus(), status)) throw new BusinessException(ErrorCode.BAD_REQUEST,"员工状态不允许由 "+employee.getStatus()+" 变更为 "+status);
        String from = employee.getStatus();
        employee.setStatus(status);
        employeeMapper.updateById(employee);
        if (!Employee.STATUS_ACTIVE.equals(status)) replan.onEmployeeEvent(id, "EMPLOYEE_STATUS_CHANGED");
        log.info("employee status changed, id={}, {} -> {}", id, from, status);
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
