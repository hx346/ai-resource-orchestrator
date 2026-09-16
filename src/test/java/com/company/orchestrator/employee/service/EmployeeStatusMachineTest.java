package com.company.orchestrator.employee.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 员工状态机：停用/休假/回归 / employee status transitions (offboarding, leave, return). */
class EmployeeStatusMachineTest {

    @Test
    void activeCanLeaveOrDeactivate() {
        assertTrue(EmployeeService.canTransition("ACTIVE", "INACTIVE"));
        assertTrue(EmployeeService.canTransition("ACTIVE", "ON_LEAVE"));
        assertFalse(EmployeeService.canTransition("ACTIVE", "ACTIVE"));
        assertFalse(EmployeeService.canTransition("ACTIVE", "BOGUS"));
    }

    @Test
    void onLeaveCanReturnOrDeactivate() {
        assertTrue(EmployeeService.canTransition("ON_LEAVE", "ACTIVE"));
        assertTrue(EmployeeService.canTransition("ON_LEAVE", "INACTIVE"));
        assertFalse(EmployeeService.canTransition("ON_LEAVE", "ON_LEAVE"));
    }

    @Test
    void inactiveOnlyReturnsToActive() {
        assertTrue(EmployeeService.canTransition("INACTIVE", "ACTIVE"));
        assertFalse(EmployeeService.canTransition("INACTIVE", "ON_LEAVE")); // 须先回归在职 / must pass through ACTIVE
        assertFalse(EmployeeService.canTransition("INACTIVE", "INACTIVE"));
    }
}
