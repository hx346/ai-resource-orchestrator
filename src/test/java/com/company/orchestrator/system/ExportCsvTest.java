package com.company.orchestrator.system;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CSV 导出：RFC4180 转义与 UTF-8 BOM / CSV escaping and BOM. */
class ExportCsvTest {

    @Test
    void escapesQuotesCommasAndNewlines() {
        assertEquals("plain", ExportService.escape("plain"));
        assertEquals("\"a,b\"", ExportService.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", ExportService.escape("say \"hi\""));
        assertEquals("\"line1\nline2\"", ExportService.escape("line1\nline2"));
    }

    @Test
    void bomCrLfAndQuotedCells() {
        var text = new String(ExportService.csv(List.of("技能", "建议"), List.of(List.of("Java", "招聘,培训"))), StandardCharsets.UTF_8);
        assertTrue(text.startsWith("﻿"));               // Excel 识别 UTF-8 的 BOM
        assertTrue(text.contains("\r\n"));                // RFC4180 行尾
        assertTrue(text.contains("\"招聘,培训\""));         // 含逗号字段加引号
    }
}
