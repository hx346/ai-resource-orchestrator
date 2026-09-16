package com.company.orchestrator.system;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 出站 Webhook 通知（Phase 5 第一片）：方案确认 / 不可用安排冲突推送到企业微信群机器人、
 * 飞书、钉钉或通用端点。事务提交后发送，任何失败只记录 notification_log，不影响业务流程。
 * Outbound webhook notifications; sent after commit, failures are logged
 * and never break the business flow. `off` disables the feature entirely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final JdbcTemplate db;
    @Value("${app.notify.mode:off}") private String mode;
    @Value("${app.notify.provider:generic}") private String provider;
    @Value("${app.notify.webhook-url:}") private String webhookUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public Map<String, Object> status() {
        return Map.of("mode", mode, "provider", provider, "configured", webhookUrl != null && !webhookUrl.isBlank());
    }

    public List<Map<String, Object>> log(int limit) {
        return db.queryForList("select id,type,provider,target,status,error,duration,created_at from notification_log order by id desc limit ?", Math.max(1, Math.min(limit, 100)));
    }

    /** 方案确认生效通知 / notify that a plan was confirmed and took effect. */
    public void planConfirmed(long projectId, int version, int items, String score) {
        var projectName = db.queryForObject("select name from project where id=?", String.class, projectId);
        afterCommit(() -> dispatch("PLAN_CONFIRMED",
                "【资源编排】项目「%s」方案 v%d 已确认生效：%d 个任务完成分配，评分 %s。".formatted(projectName, version, items, score),
                Map.of("projectId", projectId, "planVersion", version, "items", items, "score", score)));
    }

    /** 不可用安排与生效分配重叠时提醒（与重规划巡检同口径）/ notify when an availability window conflicts with active allocations. */
    public void availabilityImpact(long employeeId, String employeeName, String type, LocalDate start, LocalDate end) {
        var affected = db.queryForList("""
                select p.id project_id, p.name project_name, t.name task_name, a.start_date, a.end_date
                from resource_allocation a join project p on p.id = a.project_id join task t on t.id = a.task_id
                where a.employee_id = ? and a.status in ('PLANNED','CONFIRMED') and a.start_date <= ? and a.end_date >= ?
                order by p.id, a.start_date""", employeeId, end, start);
        if (affected.isEmpty()) return;
        var projects = affected.stream().map(r -> r.get("project_name").toString()).distinct().toList();
        afterCommit(() -> dispatch("AVAILABILITY_CONFLICT",
                "【资源编排】%s 新增 %s（%s → %s），与 %d 个项目的生效分配重叠：%s。请前往重规划巡检处理。"
                        .formatted(employeeName, type, start, end, projects.size(), String.join("、", projects)),
                Map.of("employeeId", employeeId, "employeeName", employeeName, "type", type,
                        "start", start.toString(), "end", end.toString(), "affectedProjects", affected.stream().limit(20).toList())));
    }

    /** 自动重规划触发提醒（Phase 4 余项）：detect 模式仅提醒，auto 模式附带已生成的草稿方案 / auto-replan trigger notice. */
    public void replanSuggested(long projectId, long planId, int version, int conflictCount,
            Long draftPlanId, String triggerMode, List<Map<String, Object>> conflicts) {
        var projectName = db.queryForObject("select name from project where id=?", String.class, projectId);
        afterCommit(() -> dispatch("REPLAN_SUGGESTED",
                "【资源编排】项目「%s」方案 v%d 与最新基础数据存在 %d 项冲突%s。请前往重规划处理。"
                        .formatted(projectName, version, conflictCount, draftPlanId == null ? "" : "，已自动生成重规划草稿方案 #" + draftPlanId + "（待人工确认）"),
                Map.of("projectId", projectId, "planId", planId, "planVersion", version,
                        "conflictCount", conflictCount, "draftPlanId", draftPlanId == null ? 0 : draftPlanId,
                        "triggerMode", triggerMode, "conflicts", conflicts.stream().limit(5).toList())));
    }

    /** 方案撤销通知：资源占用已释放 / notify that a plan was cancelled and its bookings released. */
    public void planCancelled(long projectId, int version) {
        var projectName = db.queryForObject("select name from project where id=?", String.class, projectId);
        afterCommit(() -> dispatch("PLAN_CANCELLED",
                "【资源编排】项目「%s」方案 v%d 已撤销，资源占用已释放。".formatted(projectName, version),
                Map.of("projectId", projectId, "planVersion", version)));
    }

    /** 项目完结通知（全部任务收尾后）/ notify that a project reached completion. */
    public void projectCompleted(long projectId) {
        var projectName = db.queryForObject("select name from project where id=?", String.class, projectId);
        afterCommit(() -> dispatch("PROJECT_COMPLETED",
                "【资源编排】项目「%s」已完结，全部任务与分配收尾。".formatted(projectName),
                Map.of("projectId", projectId)));
    }

    /** 事务内注册提交后发送；无事务时直接发送 / send after commit when inside a transaction. */
    private void afterCommit(Runnable send) {
        if (!"live".equals(mode) || webhookUrl == null || webhookUrl.isBlank()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        else send.run();
    }

    private void dispatch(String type, String message, Object data) {
        String payload = NotifyPayloads.body(provider, type, message, data);
        long started = System.currentTimeMillis();
        String error = null;
        try {
            var request = HttpRequest.newBuilder(URI.create(webhookUrl)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            int status = http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            if (status < 200 || status >= 300) error = "HTTP " + status;
        } catch (Exception ex) {
            error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            log.warn("notification failed, type={}, provider={}", type, provider, ex);
        }
        // 脱敏：webhook 地址 query 常含令牌 / mask tokens in the query string
        db.update("insert into notification_log(type,provider,target,payload,status,error,duration) values (?,?,?,?,?,?,?)",
                type, provider, webhookUrl.replaceAll("\\?.*$", ""), payload, error == null ? "SUCCESS" : "FAILED", error,
                (int) (System.currentTimeMillis() - started));
    }
}
