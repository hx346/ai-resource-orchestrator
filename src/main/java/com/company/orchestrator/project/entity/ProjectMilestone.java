package com.company.orchestrator.project.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 项目里程碑 / Project milestone. */
@Data
@TableName("project_milestone")
public class ProjectMilestone {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_SKIPPED = "SKIPPED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    private LocalDate dueDate;

    private String status;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
