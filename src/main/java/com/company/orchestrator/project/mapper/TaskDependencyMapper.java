package com.company.orchestrator.project.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.orchestrator.project.entity.TaskDependency;

@Mapper
public interface TaskDependencyMapper extends BaseMapper<TaskDependency> {
}
