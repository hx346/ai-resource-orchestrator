package com.company.orchestrator.employee.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.orchestrator.employee.entity.Department;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}
