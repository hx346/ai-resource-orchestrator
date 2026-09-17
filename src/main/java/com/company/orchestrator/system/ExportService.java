package com.company.orchestrator.system;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.company.orchestrator.allocation.ResourceTimelineService;
import com.company.orchestrator.capability.CapabilityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CSV 导出（0.9.0）：技能供需缺口、资源排期、AI 调用审计。
 * UTF-8 BOM + RFC4180 转义，Excel 可直接打开中文。
 * CSV exports for supply-demand gaps, resource bookings and the AI audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final CapabilityService capability;
    private final ResourceTimelineService timeline;
    private final JdbcTemplate db;

    /** 供需缺口：flat 一行一技能汇总；weekly 一行一技能一周 / one row per skill (flat) or skill-week (weekly). */
    @SuppressWarnings("unchecked")
    public byte[] supplyDemand(int weeks, String model) {
        if (!"flat".equals(model) && !"weekly".equals(model))
            throw new com.company.orchestrator.common.exception.BusinessException(
                    com.company.orchestrator.common.exception.ErrorCode.BAD_REQUEST, "模型仅支持 flat / weekly");
        if ("weekly".equals(model)) {
            var data = capability.supplyWeekly(weeks, null);
            var rows = new ArrayList<List<Object>>();
            for (var row : (List<Map<String, Object>>) data.get("rows"))
                for (var week : (List<Map<String, Object>>) row.get("weekly"))
                    rows.add(List.of(row.get("skillName"), "L" + row.get("requiredLevel"), week.get("week"),
                            week.get("demandHours"), week.get("supplyHours"), week.get("gapHours")));
            return csv(List.of("技能", "要求等级", "周", "需求工时", "供给工时", "缺口工时"), rows);
        }
        var data = capability.supplyDemand(weeks, "flat");
        var rows = new ArrayList<List<Object>>();
        for (var row : (List<Map<String, Object>>) data.get("rows"))
            rows.add(List.of(row.get("skillName"), "L" + row.get("requiredLevel"), row.get("taskCount"), row.get("qualifiedCount"),
                    row.get("demandHours"), row.get("availableHours"), row.get("gapHours"), row.get("gapPeople"),
                    row.get("market") == null ? "" : ((Map<?, ?>) row.get("market")).get("demandIndex"), row.get("advice")));
        return csv(List.of("技能", "要求等级", "任务数", "达标人数", "需求工时", "可供给工时", "缺口工时", "缺口人数", "市场紧张度", "建议"), rows);
    }

    /** 资源排期：一行一条生效占用 / one row per active booking. */
    @SuppressWarnings("unchecked")
    public byte[] timelineCsv() {
        var data = timeline.timeline();
        var rows = new ArrayList<List<Object>>();
        for (var row : (List<Map<String, Object>>) data.get("rows"))
            for (var booking : (List<Map<String, Object>>) row.get("bookings"))
                rows.add(List.of(row.get("name"), booking.get("projectName"), booking.get("taskName"),
                        booking.get("start"), booking.get("end"), booking.get("allocation") + "%", booking.get("status")));
        return csv(List.of("成员", "项目", "任务", "开始", "结束", "投入", "状态"), rows);
    }

    /** AI 调用审计（最近 5000 条，不含输入输出原文）/ AI audit trail, latest 5000, without prompt/response bodies. */
    public byte[] aiLog() {
        var rows = new ArrayList<List<Object>>();
        for (var row : db.queryForList("""
                select id, type, business_id, model, prompt_version, status, duration,
                       coalesce(token_usage::text, '') token_usage, created_at
                from ai_execution order by id desc limit 5000"""))
            rows.add(List.of(row.get("id"), row.get("type"), row.get("business_id"), row.get("model"), row.get("prompt_version"),
                    row.get("status"), row.get("duration"), row.get("token_usage"), row.get("created_at")));
        return csv(List.of("ID", "类型", "业务ID", "模型", "Prompt版本", "状态", "耗时ms", "Token用量", "时间"), rows);
    }

    /** UTF-8 BOM + RFC4180；含逗号/引号/换行的字段加引号转义 / BOM so Excel reads UTF-8, RFC4180 quoting. */
    static byte[] csv(List<String> headers, List<List<Object>> rows) {
        var sb = new StringBuilder();
        sb.append('﻿');
        sb.append(String.join(",", headers)).append("\r\n");
        for (var row : rows) {
            var cells = new ArrayList<String>(row.size());
            for (var cell : row) cells.add(escape(cell == null ? "" : cell.toString()));
            sb.append(String.join(",", cells)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }
}
