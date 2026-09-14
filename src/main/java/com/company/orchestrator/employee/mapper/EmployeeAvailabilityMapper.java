package com.company.orchestrator.employee.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.orchestrator.employee.entity.EmployeeAvailability;

@Mapper
public interface EmployeeAvailabilityMapper extends BaseMapper<EmployeeAvailability> {
}
