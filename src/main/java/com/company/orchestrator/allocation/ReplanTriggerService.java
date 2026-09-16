package com.company.orchestrator.allocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.company.orchestrator.system.NotifyService;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 事件自动触发重规划（Phase 4 余项）：基础数据变化（休假 / 技能画像 / 技能库 / 项目周期）
 * 提交后，异步巡检受影响的生效方案。detect 模式仅推送提醒；auto 模式额外生成重规划草稿——
 * 草稿仍须人工确认才生效，人始终是决策方。冷却时间防止重复事件刷屏。
 * Event-driven replanning: after base-data changes commit, affected active
 * plans are patrolled asynchronously. `detect` only notifies; `auto` also
 * drafts a replacement plan that still requires human confirmation. A
 * cooldown window suppresses duplicate triggers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplanTriggerService {

    private final JdbcTemplate db;
    private final ResourcePlanService plans;
    private final NotifyService notify;
    @Value("${app.replan.auto-trigger:off}") private String mode;
    @Value("${app.replan.cooldown-minutes:30}") private int cooldownMinutes;
    /** 单工作线程串行巡检，避免并发求解互相争抢 / one worker serializes patrols and solves. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "replan-trigger");
        thread.setDaemon(true);
        return thread;
    });

    /** 触发决策（纯函数，便于单测）/ pure trigger policy. */
    static String policy(boolean hasConflicts, boolean draftExists, long minutesSinceLast, int cooldownMinutes) {
        if (!hasConflicts) return null;
        if (draftExists) return "SKIPPED_DRAFT";
        if (minutesSinceLast < cooldownMinutes) return "SKIPPED_COOLDOWN";
        return "TRIGGER";
    }

    /** 休假 / 画像等员工维度事件 → 其生效分配所在项目 / employee-scoped events map to their booked projects. */
    public void onEmployeeEvent(long employeeId, String event) {
        var projectIds = db.queryForList("select distinct project_id from resource_allocation where employee_id=? and status='CONFIRMED'", Long.class, employeeId);
        for (long projectId : projectIds) enqueue(projectId, event, employeeId);
    }

    /** 项目周期变化 → 该项目 / project-scoped event. */
    public void onProjectEvent(long projectId, String event) { enqueue(projectId, event, null); }

    /** 技能库变化 → 要求该技能的生效方案 / skill-scoped event hits plans requiring the skill. */
    public void onSkillEvent(long skillId, String event) {
        var projectIds = db.queryForList("""
                select distinct a.project_id from resource_allocation a
                join task_skill_requirement r on r.task_id=a.task_id
                where r.skill_id=? and a.status='CONFIRMED'""", Long.class, skillId);
        for (long projectId : projectIds) enqueue(projectId, event, null);
    }

    /** 触发记录（配置页 / 审计）/ recent trigger rows for the settings page and audit. */
    public List<Map<String, Object>> triggers(int limit) {
        return db.queryForList("select id,source_event,employee_id,project_id,plan_id,conflict_count,action,detail,created_at from replan_trigger_log order by id desc limit ?",
                Math.max(1, Math.min(limit, 100)));
    }

    private void enqueue(long projectId, String event, Long employeeId) {
        if (!"detect".equals(mode) && !"auto".equals(mode)) return;
        var task = (Runnable) () -> evaluate(projectId, event, employeeId);
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit(projectId, task); }
            });
        else submit(projectId, task);
    }

    private void submit(long projectId, Runnable task) {
        worker.submit(() -> {
            try { task.run(); }
            catch (Exception ex) { log.error("replan trigger failed, projectId={}", projectId, ex); }
        });
    }

    @SuppressWarnings("unchecked")
    private void evaluate(long projectId, String event, Long employeeId) {
        var active = db.queryForList("select id, version from resource_plan where project_id=? and status='CONFIRMED' order by version desc limit 1", projectId);
        if (active.isEmpty()) return;
        long planId = ((Number) active.getFirst().get("id")).longValue();
        int version = ((Number) active.getFirst().get("version")).intValue();
        var conflicts = ((List<?>) plans.impact(projectId).get("conflicts")).stream().map(c -> (Map<String, Object>) c).toList();
        boolean draftExists = db.queryForObject("select count(*) from resource_plan where project_id=? and status='DRAFT'", Long.class, projectId) > 0;
        var action = policy(!conflicts.isEmpty(), draftExists, minutesSinceLast(planId), cooldownMinutes);
        if (action == null) return;
        if (!"TRIGGER".equals(action)) { record(event, employeeId, projectId, planId, conflicts.size(), action, null); return; }
        Long draftPlanId = null;
        String result = "NOTIFIED";
        String detail = null;
        if ("auto".equals(mode)) {
            // 沿用当前生效方案的求解策略，而非固定 BALANCED / keep the active plan's strategy
            var strategyRows = db.queryForList("select strategy from resource_plan where id=?", String.class, planId);
            var strategy = strategyRows.isEmpty() ? null : strategyRows.getFirst();
            try { draftPlanId = plans.replan(projectId, strategy == null ? "BALANCED" : strategy, "auto-replan"); result = "DRAFTED"; }
            catch (RuntimeException ex) { result = "FAILED"; detail = ex.getMessage(); log.warn("auto replan solve failed, projectId={}", projectId, ex); }
        }
        record(event, employeeId, projectId, planId, conflicts.size(), result, detail);
        notify.replanSuggested(projectId, planId, version, conflicts.size(), draftPlanId, mode, conflicts);
    }

    private long minutesSinceLast(long planId) {
        var minutes = db.queryForObject("select extract(epoch from now() - max(created_at)) / 60 from replan_trigger_log where plan_id=? and action in ('NOTIFIED','DRAFTED','FAILED')", Double.class, planId);
        return minutes == null ? Long.MAX_VALUE : minutes.longValue();
    }

    private void record(String event, Long employeeId, long projectId, long planId, int conflicts, String action, String detail) {
        db.update("insert into replan_trigger_log(source_event,employee_id,project_id,plan_id,conflict_count,action,detail) values (?,?,?,?,?,?,?)",
                event, employeeId, projectId, planId, conflicts, action, detail == null ? null : detail.substring(0, Math.min(512, detail.length())));
        log.info("replan trigger, event={}, projectId={}, planId={}, conflicts={}, action={}", event, projectId, planId, conflicts, action);
    }

    @PreDestroy void shutdown() { worker.shutdownNow(); }
}
