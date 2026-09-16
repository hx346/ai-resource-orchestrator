package com.company.orchestrator.integration;

import java.time.LocalDate;
import java.util.List;

/**
 * 同步映射纯函数：外部状态 / 优先级 / 工时归一到 ARO 口径，以及导入任务的
 * 默认顺序排期（按工时排工作日、跳过周末）与项目窗口推算。
 * Pure mapping helpers: normalize external status / priority / hours into ARO
 * terms, plus the default sequential workday schedule and window suggestion.
 */
public final class SyncMappers {

    private SyncMappers() {}

    /** 工时上限（与任务校验口径一致）/ hour cap consistent with task validation. */
    private static final int MAX_HOURS = 2000;

    /** Jira statusCategory key → ARO 状态 / Jira status category to ARO status. */
    public static String jiraStatus(String categoryKey) {
        return switch (categoryKey == null ? "" : categoryKey.toLowerCase()) {
            case "done" -> "DONE";
            case "indeterminate" -> "IN_PROGRESS";
            default -> "TODO";
        };
    }

    /** 禅道 / 通用状态词 → ARO 状态 / ZenTao & generic status words to ARO status. */
    public static String genericStatus(String raw) {
        var value = raw == null ? "" : raw.toLowerCase();
        if (value.contains("close") || value.contains("done") || value.contains("cancel") || value.contains("关闭") || value.contains("取消") || value.contains("已完成")) return "DONE";
        if (value.contains("doing") || value.contains("progress") || value.contains("进行")) return "IN_PROGRESS";
        return "TODO";
    }

    /** GitLab issue state → ARO 状态 / GitLab issue state. */
    public static String gitlabStatus(String state) { return "closed".equals(state) ? "DONE" : "TODO"; }

    /** 优先级钳到 1–5 / clamp priority into 1..5. */
    public static int priority(Integer value) {
        return value == null ? 3 : Math.max(1, Math.min(5, value));
    }

    /** 禅道 pri 1–4（1 最高）→ ARO 1–5 / ZenTao pri mapping. */
    public static int zentaoPriority(Integer pri) {
        if (pri == null) return 3;
        return switch (pri) { case 1 -> 1; case 2 -> 2; case 3 -> 4; case 4 -> 5; default -> 3; };
    }

    /** 秒 → 小时（Jira timeestimate），空 / 非法回退默认 / seconds to hours with fallback. */
    public static int hours(Integer seconds, int fallback) {
        int hours = seconds == null || seconds <= 0 ? fallback : (int) Math.round(seconds / 3600.0);
        return Math.max(1, Math.min(MAX_HOURS, hours));
    }

    /** 工时钳到 1–2000 / clamp hours. */
    public static int hours(int hours) { return Math.max(1, Math.min(MAX_HOURS, hours)); }

    /**
     * 顺序排期：从 start 起跳过周末排 workdays 个工作日，返回 [起, 止]。
     * Sequential schedule skipping weekends; returns [firstDay, lastDay].
     */
    public static LocalDate[] schedule(LocalDate start, int workdays) {
        int days = Math.max(1, workdays);
        var first = start;
        while (first.getDayOfWeek().getValue() > 5) first = first.plusDays(1);
        var cursor = first;
        int placed = 1;
        while (placed < days) {
            cursor = cursor.plusDays(1);
            if (cursor.getDayOfWeek().getValue() <= 5) placed++;
        }
        return new LocalDate[]{first, cursor};
    }

    /** 建议项目窗口：覆盖总工时所需工作日 + 一周缓冲，上限两年 / window covering the backlog. */
    public static LocalDate windowEnd(LocalDate start, List<Integer> hours) {
        int workdays = hours.stream().mapToInt(h -> (int) Math.ceil(h / 8.0)).sum();
        var end = schedule(start, Math.max(1, workdays))[1].plusWeeks(1);
        return end.isAfter(start.plusYears(2)) ? start.plusYears(2) : end;
    }
}
