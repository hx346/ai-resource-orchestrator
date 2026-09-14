package com.company.orchestrator.skill.entity;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 技能分类 / Skill category. */
@Data
@TableName("skill_category")
public class SkillCategory {

    public static final String STATUS_ACTIVE = "ACTIVE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private Integer sortOrder;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
