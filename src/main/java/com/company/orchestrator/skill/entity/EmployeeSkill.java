package com.company.orchestrator.skill.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.orchestrator.common.enums.SkillSource;

import lombok.Data;

/** 员工技能画像（L1~L5）/ Employee skill profile. */
@Data
@TableName("employee_skill")
public class EmployeeSkill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long employeeId;

    private Long skillId;

    /** 技能等级 1~5 / Skill level 1-5. */
    private Integer level;

    private Integer experienceMonths;

    private SkillSource source;

    /** AI 置信度 0~1 / Confidence 0-1. */
    private BigDecimal confidence;

    private Boolean verified;

    private LocalDate lastUsedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
