package com.company.orchestrator.project.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.orchestrator.common.enums.RequirementType;

import lombok.Data;

/** 任务技能需求 / Task skill requirement. */
@Data
@TableName("task_skill_requirement")
public class TaskSkillRequirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long skillId;

    /** 最低等级 1~5 / Minimum level 1-5. */
    private Integer minLevel;

    /** 权重 0~1 / Weight 0-1. */
    private BigDecimal weight;

    private RequirementType requirementType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
