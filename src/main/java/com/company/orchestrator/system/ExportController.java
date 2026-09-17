package com.company.orchestrator.system;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** CSV 导出接口 / CSV export API. 供需与排期对全部登录角色开放；AI 审计走 /system 前缀仅管理员。 */
@Tag(name = "Export", description = "CSV 导出 / CSV export")
@RestController
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exports;

    @Operation(summary = "导出技能供需缺口 CSV（flat 汇总 / weekly 逐周）/ Export supply-demand gaps as CSV")
    @GetMapping("/api/v1/export/supply-demand")
    public ResponseEntity<byte[]> supplyDemand(@RequestParam(defaultValue = "12") int weeks,
            @RequestParam(defaultValue = "flat") String model) {
        return file("supply-demand-" + model + "-" + LocalDate.now() + ".csv", exports.supplyDemand(weeks, model));
    }

    @Operation(summary = "导出资源排期（生效占用明细）/ Export active bookings as CSV")
    @GetMapping("/api/v1/export/timeline")
    public ResponseEntity<byte[]> timeline() {
        return file("timeline-" + LocalDate.now() + ".csv", exports.timelineCsv());
    }

    @Operation(summary = "导出 AI 调用审计 CSV（仅管理员）/ Export the AI audit trail as CSV (admin only)")
    @GetMapping("/api/v1/system/export/ai-log")
    public ResponseEntity<byte[]> aiLog() {
        return file("ai-log-" + LocalDate.now() + ".csv", exports.aiLog());
    }

    private static ResponseEntity<byte[]> file(String name, byte[] body) {
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv;charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .body(body);
    }
}
