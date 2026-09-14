package com.company.orchestrator.employee.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 员工 / Employee. */
@Data
@TableName("employee")
public class Employee {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_ON_LEAVE = "ON_LEAVE";

    public static final int DEFAULT_WEEKLY_HOURS = 40;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String employeeNo;

    private String name;

    private Long departmentId;

    private String position;

    private String location;

    private String status;

    private Integer weeklyHours;

    /** 默认可用容量百分比 0~100 / Default available capacity in percent. */
    private BigDecimal defaultCapacity;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
