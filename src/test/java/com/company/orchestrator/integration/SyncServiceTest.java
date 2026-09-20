package com.company.orchestrator.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 刷新冻结策略：双方同为终态才保留本地排期 / refresh freeze policy: only both-terminal tasks keep local scheduling. */
class SyncServiceTest {

    @Test
    void keepsLocalScheduleOnlyWhenBothSidesTerminalAndEqual() {
        assertTrue(SyncService.keepLocalSchedule("DONE", "DONE"));
        assertTrue(SyncService.keepLocalSchedule("CANCELLED", "CANCELLED"));
    }

    @Test
    void refreezesNothingWhenRemoteReopensOrStatusDiffers() {
        assertFalse(SyncService.keepLocalSchedule("DONE", "IN_PROGRESS"));   // 远端重开 → 照常更新 / remote reopened
        assertFalse(SyncService.keepLocalSchedule("CANCELLED", "TODO"));
        assertFalse(SyncService.keepLocalSchedule("TODO", "TODO"));          // 双方未完结 → 参与重排
        assertFalse(SyncService.keepLocalSchedule(null, "DONE"));           // 新任务 → 正常插入
    }
}
