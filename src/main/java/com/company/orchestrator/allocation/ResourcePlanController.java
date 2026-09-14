package com.company.orchestrator.allocation;
import java.util.*;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.company.orchestrator.common.result.Result;
@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class ResourcePlanController {
    private final ResourcePlanService service;
    private final ResourceTimelineService timeline;
    public record SolveRequest(String strategy) {}
    @GetMapping("/allocations/timeline") public Result<?> timeline() { return Result.ok(timeline.timeline()); }
    @GetMapping("/projects/{id}/candidates") public Result<?> candidates(@PathVariable long id) { return Result.ok(service.candidates(id)); }
    @PostMapping("/projects/{id}/solve") public Result<Long> solve(@PathVariable long id,@RequestBody(required=false) SolveRequest r,Principal p) { return Result.ok(service.solve(id,r==null||r.strategy()==null?"BALANCED":r.strategy(),p.getName())); }
    @GetMapping("/projects/{id}/resource-plans") public Result<?> list(@PathVariable long id) { return Result.ok(service.list(id)); }
    @GetMapping("/resource-plans/{id}") public Result<?> get(@PathVariable long id) { return Result.ok(service.get(id)); }
    @GetMapping("/resource-plans/compare") public Result<?> compare(@RequestParam long left,@RequestParam long right) { return Result.ok(service.compare(left,right)); }
    @PutMapping("/resource-plans/{id}/items") public Result<?> edit(@PathVariable long id,@RequestBody List<ResourcePlanService.Selection> r) { service.edit(id,r); return Result.ok(service.get(id)); }
    @PostMapping("/resource-plans/{id}/confirm") public Result<?> confirm(@PathVariable long id) { service.confirm(id); return Result.ok(service.get(id)); }
    @PostMapping("/resource-plans/{id}/cancel") public Result<?> cancel(@PathVariable long id) { service.cancel(id); return Result.ok(service.get(id)); }
}
