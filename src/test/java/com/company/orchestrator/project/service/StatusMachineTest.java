package com.company.orchestrator.project.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 生命周期状态机（任务/项目）：流转白名单与终态 / lifecycle transition maps for tasks and projects. */
class StatusMachineTest {

    @Test
    void taskFlowsForwardAndTerminates() {
        assertTrue(TaskService.canTransition("TODO", "IN_PROGRESS"));
        assertTrue(TaskService.canTransition("TODO", "DONE"));          // 小任务可直接完成 / direct completion allowed
        assertTrue(TaskService.canTransition("TODO", "CANCELLED"));
        assertTrue(TaskService.canTransition("IN_PROGRESS", "DONE"));
        assertTrue(TaskService.canTransition("IN_PROGRESS", "TODO"));   // 返工 / rework
        assertTrue(TaskService.canTransition("IN_PROGRESS", "CANCELLED"));
        assertFalse(TaskService.canTransition("DONE", "TODO"));         // 终态 / terminal
        assertFalse(TaskService.canTransition("CANCELLED", "TODO"));
        assertFalse(TaskService.canTransition("CANCELLED", "DONE"));
        assertFalse(TaskService.canTransition("TODO", "TODO"));
        assertFalse(TaskService.canTransition("TODO", "BOGUS"));
    }

    @Test
    void projectFlowsThroughLifecycle() {
        assertTrue(ProjectService.canTransition("PLANNING", "IN_PROGRESS"));
        assertTrue(ProjectService.canTransition("PLANNING", "CANCELLED"));
        assertTrue(ProjectService.canTransition("IN_PROGRESS", "ON_HOLD"));
        assertTrue(ProjectService.canTransition("IN_PROGRESS", "COMPLETED"));
        assertTrue(ProjectService.canTransition("IN_PROGRESS", "CANCELLED"));
        assertTrue(ProjectService.canTransition("ON_HOLD", "IN_PROGRESS"));
        assertTrue(ProjectService.canTransition("ON_HOLD", "CANCELLED"));
        assertFalse(ProjectService.canTransition("PLANNING", "COMPLETED"));  // 未启动不可直接完结 / no shortcut to completion
        assertFalse(ProjectService.canTransition("ON_HOLD", "COMPLETED"));
        assertFalse(ProjectService.canTransition("IN_PROGRESS", "PLANNING"));
        assertFalse(ProjectService.canTransition("COMPLETED", "PLANNING"));  // 终态 / terminal
        assertFalse(ProjectService.canTransition("CANCELLED", "PLANNING"));
        assertFalse(ProjectService.canTransition("PLANNING", "BOGUS"));
    }
}
