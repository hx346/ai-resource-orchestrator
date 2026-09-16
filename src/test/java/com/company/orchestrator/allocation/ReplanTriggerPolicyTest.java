package com.company.orchestrator.allocation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 自动重规划触发策略：无冲突静默、草稿 / 冷却期抑制、达到条件触发 / trigger policy matrix. */
class ReplanTriggerPolicyTest {

    @Test
    void noConflictsStaysSilent() {
        assertNull(ReplanTriggerService.policy(false, false, Long.MAX_VALUE, 30));
    }

    @Test
    void existingDraftSuppressesTrigger() {
        assertEquals("SKIPPED_DRAFT", ReplanTriggerService.policy(true, true, Long.MAX_VALUE, 30));
    }

    @Test
    void cooldownSuppressesRepeatTriggers() {
        assertEquals("SKIPPED_COOLDOWN", ReplanTriggerService.policy(true, false, 29, 30));
        assertEquals("SKIPPED_COOLDOWN", ReplanTriggerService.policy(true, false, 0, 30));
    }

    @Test
    void conflictsBeyondCooldownTrigger() {
        assertEquals("TRIGGER", ReplanTriggerService.policy(true, false, 30, 30));
        assertEquals("TRIGGER", ReplanTriggerService.policy(true, false, Long.MAX_VALUE, 30));
    }
}
