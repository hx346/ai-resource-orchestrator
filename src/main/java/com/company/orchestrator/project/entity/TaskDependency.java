package com.company.orchestrator.project.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.orchestrator.common.enums.DependencyType;

import lombok.Data;

/** 任务依赖 / Task dependency. */
@Data
@TableName("task_dependency")
public class TaskDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long predecessorTaskId;

    private Long successorTaskId;

    private DependencyType dependencyType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
