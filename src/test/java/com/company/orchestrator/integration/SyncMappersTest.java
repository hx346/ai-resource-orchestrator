package com.company.orchestrator.integration;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 同步映射：状态 / 优先级 / 工时归一与顺序排期 / sync mappers: normalization and sequential scheduling. */
class SyncMappersTest {

    @Test
    void jiraStatusCategoriesMapToAroTerms() {
        assertEquals("DONE", SyncMappers.jiraStatus("done"));
        assertEquals("IN_PROGRESS", SyncMappers.jiraStatus("indeterminate"));
        assertEquals("TODO", SyncMappers.jiraStatus("new"));
        assertEquals("TODO", SyncMappers.jiraStatus(null));
    }

    @Test
    void genericAndGitlabStatuses() {
        assertEquals("DONE", SyncMappers.genericStatus("closed"));
        assertEquals("DONE", SyncMappers.genericStatus("已取消"));
        assertEquals("IN_PROGRESS", SyncMappers.genericStatus("doing"));
        assertEquals("TODO", SyncMappers.genericStatus("wait"));
        assertEquals("DONE", SyncMappers.gitlabStatus("closed"));
        assertEquals("TODO", SyncMappers.gitlabStatus("opened"));
    }

    @Test
    void prioritiesAndHoursClamp() {
        assertEquals(1, SyncMappers.priority(0));
        assertEquals(5, SyncMappers.priority(9));
        assertEquals(3, SyncMappers.priority(null));
        assertEquals(1, SyncMappers.zentaoPriority(1));
        assertEquals(5, SyncMappers.zentaoPriority(4));
        assertEquals(3, SyncMappers.zentaoPriority(null));
        assertEquals(8, SyncMappers.hours(null, 8));
        assertEquals(3, SyncMappers.hours(10800, 8));    // 3h 估时
        assertEquals(1, SyncMappers.hours(3600, 8));     // 1h 估时按 1h 落地
        assertEquals(2000, SyncMappers.hours(99999999));
        assertEquals(1, SyncMappers.hours(0));
    }

    @Test
    void scheduleSkipsWeekends() {
        // 2026-10-03 是周六：3 个工作日应从周一 05 排到周三 07
        var window = SyncMappers.schedule(LocalDate.of(2026, 10, 3), 3);
        assertEquals(LocalDate.of(2026, 10, 5), window[0]);
        assertEquals(LocalDate.of(2026, 10, 7), window[1]);
        // 跨周末：10-09（周五）起 2 个工作日 → 09 与 12
        var across = SyncMappers.schedule(LocalDate.of(2026, 10, 9), 2);
        assertEquals(LocalDate.of(2026, 10, 9), across[0]);
        assertEquals(LocalDate.of(2026, 10, 12), across[1]);
        // 0 工作日按至少 1 天处理，从周日顺延到周一 / zero workdays fall back to one day
        var minimum = SyncMappers.schedule(LocalDate.of(2026, 10, 4), 0);
        assertEquals(LocalDate.of(2026, 10, 5), minimum[0]);
        assertEquals(LocalDate.of(2026, 10, 5), minimum[1]);
    }

    @Test
    void windowEndCoversBacklogWithBuffer() {
        // 40h ≈ 5 个工作日 + 一周缓冲；上限两年
        var end = SyncMappers.windowEnd(LocalDate.of(2026, 10, 5), List.of(8, 8, 8, 8, 8));
        assertEquals(LocalDate.of(2026, 10, 16), end);   // 05–09 五个工作日 + 一周缓冲
        assertEquals(LocalDate.of(2028, 10, 5), SyncMappers.windowEnd(LocalDate.of(2026, 10, 5), List.of(2000, 2000, 2000)));
    }
}
