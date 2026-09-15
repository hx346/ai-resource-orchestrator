package com.company.orchestrator.capability;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 组织能力决策接口（只读分析）/ Organizational capability analytics (read-only). */
@Tag(name = "Capability", description = "组织能力决策 / Organizational capability decisions")
@RestController
@RequestMapping("/api/v1/capability")
@RequiredArgsConstructor
public class CapabilityController {

    private final CapabilityService service;

    @Operation(summary = "技能供需 Gap 预测 / Skill supply-demand gap forecast")
    @GetMapping("/supply-demand")
    public Result<Map<String, Object>> supplyDemand(@RequestParam(defaultValue = "12") int weeks) {
        return Result.ok(service.supplyDemand(weeks));
    }

    @Operation(summary = "Pipeline 情景模拟：待启动项目全部并行 / What-if when queued projects all start")
    @GetMapping("/scenario")
    public Result<Map<String, Object>> scenario(@RequestParam(defaultValue = "26") int weeks,
            @RequestParam(required = false) List<Long> projectIds) {
        return Result.ok(service.scenario(weeks, projectIds));
    }

    @Operation(summary = "缺口趋势：8/12/26 周对比 / Gap trend across three windows")
    @GetMapping("/trends")
    public Result<Map<String, Object>> trends() {
        return Result.ok(service.trends());
    }

    @Operation(summary = "AI 缺口建议（招聘/培训/外包/调配，不决策）/ AI advice over the gaps")
    @PostMapping("/advise")
    public Result<Map<String, String>> advise(@RequestParam(defaultValue = "12") int weeks) {
        return Result.ok(service.advise(weeks));
    }

    @Operation(summary = "关键能力节点 / Key capability nodes (bottleneck-skill holders)")
    @GetMapping("/key-people")
    public Result<?> keyPeople() {
        return Result.ok(service.keyPeople());
    }

    @Operation(summary = "核心人员离开影响（what-if）/ Impact if a person becomes unavailable")
    @GetMapping("/leave-impact")
    public Result<?> leaveImpact(@RequestParam long employeeId) {
        return Result.ok(service.leaveImpact(employeeId));
    }
}
