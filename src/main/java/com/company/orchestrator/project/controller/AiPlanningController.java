package com.company.orchestrator.project.controller;
import java.util.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.company.orchestrator.ai.PlanDraft;
import com.company.orchestrator.project.service.AiPlanningService;
import com.company.orchestrator.common.result.Result;
@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class AiPlanningController {
    private final AiPlanningService service;
    @GetMapping("/ai/status") public Result<?> status() { return Result.ok(Map.of("mode",service.mode())); }
    @PostMapping("/projects/{id}/ai-plan") public Result<?> generate(@PathVariable long id) { return Result.ok(service.generate(id)); }
    @PostMapping(value="/projects/{id}/ai-plan/stream",produces="text/event-stream") public SseEmitter stream(@PathVariable long id) {
        var emitter=new SseEmitter(90000L);
        Thread worker=Thread.ofVirtual().unstarted(() -> {
            try {
                emitter.send(SseEmitter.event().name("progress").data("正在分析项目并生成任务规划…"));
                var draft=service.generate(id);
                emitter.send(SseEmitter.event().name("progress").data("任务、技能及依赖校验完成"));
                emitter.send(SseEmitter.event().name("result").data(draft));
                emitter.complete();
            } catch(Exception ex) {
                try { emitter.send(SseEmitter.event().name("error").data("规划未完成，请检查 AI 配置及项目日期后重试")); emitter.complete(); } catch(Exception ignored) { emitter.completeWithError(ex); }
            }
        });
        emitter.onTimeout(worker::interrupt); emitter.onError(ex -> worker.interrupt()); worker.start(); return emitter;
    }
    @PostMapping("/projects/{id}/ai-plan/accept") public Result<?> accept(@PathVariable long id,@Valid @RequestBody PlanDraft draft) { return Result.ok(service.accept(id,draft)); }
    @PostMapping("/resource-plans/{id}/review") public Result<?> review(@PathVariable long id) { return Result.ok(service.review(id)); }
    @PostMapping("/resource-plans/{id}/explain-diff") public Result<?> explainDiff(@PathVariable long id) { return Result.ok(service.explainReplan(id)); }
}
