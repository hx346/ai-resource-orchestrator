package com.company.orchestrator.employee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.company.orchestrator.common.enums.AvailabilityType;
import com.company.orchestrator.employee.entity.EmployeeAvailability;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 员工可用性创建入参 / Availability create request. */
public record AvailabilityUpsertRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal capacity,
        @NotNull AvailabilityType type,
        @Size(max = 512) String remark) {

    public EmployeeAvailability toEntity(Long employeeId) {
        EmployeeAvailability availability = new EmployeeAvailability();
        availability.setEmployeeId(employeeId);
        availability.setStartDate(startDate);
        availability.setEndDate(endDate);
        availability.setCapacity(capacity);
        availability.setType(type);
        availability.setRemark(remark);
        return availability;
    }

    public static AvailabilityUpsertRequest from(EmployeeAvailability availability) {
        return new AvailabilityUpsertRequest(availability.getStartDate(), availability.getEndDate(),
                availability.getCapacity(), availability.getType(), availability.getRemark());
    }
}
