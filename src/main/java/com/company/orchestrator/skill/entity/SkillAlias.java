package com.company.orchestrator.skill.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 技能别名（归一化用）/ Skill alias for normalization. */
@Data
@TableName("skill_alias")
public class SkillAlias {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skillId;

    private String alias;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
