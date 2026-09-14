package com.company.orchestrator.employee.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.orchestrator.employee.entity.Employee;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
