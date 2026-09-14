package com.company.orchestrator.employee.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.orchestrator.common.enums.AvailabilityType;

import lombok.Data;

/** 员工可用性（某时间段的容量/休假/出差等）/ Employee availability window. */
@Data
@TableName("employee_availability")
public class EmployeeAvailability {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long employeeId;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 该时间段容量百分比 0~100 / Capacity in percent for this window. */
    private BigDecimal capacity;

    private AvailabilityType type;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
