package com.company.orchestrator.capability;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** 组织能力决策接口（分析只读；市场数据为人工导入的外部参考）/ Organizational capability analytics (read-only; market data is admin-imported). */
@Tag(name = "Capability", description = "组织能力决策 / Organizational capability decisions")
@RestController
@RequestMapping("/api/v1/capability")
@RequiredArgsConstructor
public class CapabilityController {

    private final CapabilityService service;

    @Operation(summary = "技能供需 Gap 预测（model=flat 平铺 / weekly 按周精化）/ Skill gap forecast, flat or weekly model")
    @GetMapping("/supply-demand")
    public Result<Map<String, Object>> supplyDemand(@RequestParam(defaultValue = "12") int weeks,
            @RequestParam(defaultValue = "flat") String model) {
        return Result.ok(service.supplyDemand(weeks, model));
    }

    @Operation(summary = "按周供给明细：逐周需求 / 供给 / 缺口 / Weekly supply breakdown per skill")
    @GetMapping("/supply-weekly")
    public Result<Map<String, Object>> supplyWeekly(@RequestParam(defaultValue = "12") int weeks,
            @RequestParam(required = false) Long skillId) {
        return Result.ok(service.supplyWeekly(weeks, skillId));
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

    @Operation(summary = "导入技能市场参考数据（外部来源）/ Import external market benchmarks per skill")
    @PostMapping("/market/import")
    public Result<Map<String, Object>> marketImport(@Valid @RequestBody MarketImportRequest request) {
        return Result.ok(service.marketImport(request.source(), request.items()));
    }

    @Operation(summary = "市场参考数据列表 / List imported market benchmarks")
    @GetMapping("/market")
    public Result<List<Map<String, Object>>> market() {
        return Result.ok(service.marketList());
    }

    @Operation(summary = "删除技能的市场数据 / Delete market data of a skill")
    @DeleteMapping("/market/{skillId}")
    public Result<Void> marketDelete(@PathVariable long skillId) {
        service.marketDelete(skillId);
        return Result.ok();
    }

    /** 市场数据导入入参 / market import payload. */
    public record MarketImportRequest(@NotBlank @Size(max = 64) String source, @NotEmpty @Valid List<Item> items) {
        public record Item(@NotNull Long skillId,
                @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal demandIndex,
                @DecimalMin("0") BigDecimal salaryMin,
                @DecimalMin("0") BigDecimal salaryMax,
                @Min(0) @Max(104) Integer hiringLeadWeeks,
                @Size(max = 512) String note) {
        }
    }
}
