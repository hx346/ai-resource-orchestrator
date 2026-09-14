package com.company.orchestrator.employee.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.employee.dto.AvailabilityUpsertRequest;
import com.company.orchestrator.employee.entity.EmployeeAvailability;
import com.company.orchestrator.employee.mapper.EmployeeAvailabilityMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 员工可用性管理（休假/出差/容量窗口）/ Employee availability management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeAvailabilityService {

    private final EmployeeAvailabilityMapper availabilityMapper;
    private final EmployeeService employeeService;

    public List<EmployeeAvailability> listByEmployee(Long employeeId) {
        return availabilityMapper.selectList(new LambdaQueryWrapper<EmployeeAvailability>()
                .eq(EmployeeAvailability::getEmployeeId, employeeId)
                .orderByAsc(EmployeeAvailability::getStartDate));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long employeeId, AvailabilityUpsertRequest request) {
        employeeService.requireExists(employeeId);
        EmployeeAvailability availability = request.toEntity(employeeId);
        availabilityMapper.insert(availability);
        return availability.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EmployeeAvailability availability = availabilityMapper.selectById(id);
        if (availability == null) {
            throw new BusinessException(ErrorCode.AVAILABILITY_NOT_FOUND, id);
        }
        availabilityMapper.deleteById(id);
    }
}
