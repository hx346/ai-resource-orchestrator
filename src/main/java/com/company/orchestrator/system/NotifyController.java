package com.company.orchestrator.system;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 出站通知状态与记录（仅管理员）/ notification status and log (admin only). */
@Tag(name = "Notify", description = "出站通知 / Outbound notifications")
@RestController
@RequestMapping("/api/v1/system/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @Operation(summary = "通知配置状态 / Notification configuration status")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(notifyService.status());
    }

    @Operation(summary = "最近通知记录 / Recent notification log")
    @GetMapping("/log")
    public Result<?> log(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(notifyService.log(limit));
    }
}
