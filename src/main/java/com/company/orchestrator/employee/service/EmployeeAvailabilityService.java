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
    private final com.company.orchestrator.system.NotifyService notify;
    private final com.company.orchestrator.allocation.ReplanTriggerService replan;

    public List<EmployeeAvailability> listByEmployee(Long employeeId) {
        return availabilityMapper.selectList(new LambdaQueryWrapper<EmployeeAvailability>()
                .eq(EmployeeAvailability::getEmployeeId, employeeId)
                .orderByAsc(EmployeeAvailability::getStartDate));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long employeeId, AvailabilityUpsertRequest request) {
        var employee = employeeService.requireExists(employeeId);
        if(request.endDate().isBefore(request.startDate())) throw new BusinessException(ErrorCode.BAD_REQUEST,"结束日期早于开始日期");
        EmployeeAvailability availability = request.toEntity(employeeId);
        availabilityMapper.insert(availability);
        // 与生效分配重叠时在提交后推送冲突提醒（off 模式为空操作）/ conflict notification after commit; no-op when off
        notify.availabilityImpact(employeeId, employee.getName(), availability.getType().name(), availability.getStartDate(), availability.getEndDate());
        // 自动重规划巡检（off 模式为空操作）/ auto-replan patrol after commit; no-op when off
        replan.onEmployeeEvent(employeeId, "AVAILABILITY_CHANGED");
        return availability.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long employeeId, Long id) {
        EmployeeAvailability availability = availabilityMapper.selectById(id);
        if (availability == null) {
            throw new BusinessException(ErrorCode.AVAILABILITY_NOT_FOUND, id);
        }
        if (!availability.getEmployeeId().equals(employeeId)) throw new BusinessException(ErrorCode.BAD_REQUEST,"记录不属于指定资源");
        availabilityMapper.deleteById(id);
        replan.onEmployeeEvent(employeeId, "AVAILABILITY_CHANGED");
    }
}
