package com.company.orchestrator.integration;

import static com.company.orchestrator.solver.PlanningRepository.bad;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.orchestrator.integration.ExternalProjectClient.ProjectWithTasks;
import com.company.orchestrator.integration.ExternalProjectClient.RemoteTask;

import lombok.extern.slf4j.Slf4j;

/**
 * 外部项目单向导入：拉取 → 建项目（PLANNING）→ 任务按默认顺序排期落地 →
 * integration_link 记录映射。无生效分配时可增量刷新（新增 / 更新 / 远端已删则取消）。
 * 集成只搬运事实：技能需求不导入，目标与约束由人维护。
 * One-way external import: fetch → create a PLANNING project → land tasks on a
 * default sequential schedule → record integration_link rows. Refresh is only
 * allowed while no allocation set is active. Facts only — skill requirements
 * stay human-authored.
 */
@Slf4j
@Service
public class SyncService {

    private final JdbcTemplate db;
    private final Map<String, ExternalProjectClient> clients;

    public SyncService(JdbcTemplate db, List<ExternalProjectClient> list) {
        this.db = db;
        this.clients = list.stream().collect(Collectors.toMap(c -> c.source().toLowerCase(), c -> c));
    }

    /** 列出外部项目（关键词本地过滤）/ list remote projects filtered by keyword. */
    public List<Map<String, Object>> list(String source, String keyword) {
        var client = client(source);
        return client.listProjects().stream()
                .filter(p -> keyword == null || keyword.isBlank() || p.name().toLowerCase().contains(keyword.toLowerCase()))
                .map(p -> Map.<String, Object>of("externalId", p.externalId(), "key", p.key(), "name", p.name(),
                        "status", p.status() == null ? "" : p.status(), "url", p.url() == null ? "" : p.url()))
                .toList();
    }

    /** 导入外部项目 / import a remote project. */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importProject(String source, String externalId) {
        var client = client(source);
        if (externalId == null || externalId.isBlank()) bad("缺少外部项目 ID");
        var existing = db.queryForList("select internal_id from integration_link where source=? and external_type='PROJECT' and external_id=?",
                Long.class, client.source(), externalId);
        if (!existing.isEmpty()) return Map.of("alreadyImported", true, "projectId", existing.getFirst(), "note", "该项目此前已导入，可执行刷新增量同步");
        ProjectWithTasks fetched = client.fetch(externalId);
        LocalDate start = LocalDate.now();
        LocalDate end = SyncMappers.windowEnd(start, fetched.tasks().stream().map(RemoteTask::hours).toList());
        long projectId = db.queryForObject("""
                insert into project(name, description, priority, status, start_date, end_date)
                values (?,?,3,'PLANNING',?,?) returning id""", Long.class,
                fetched.project().name(), "从 " + client.source() + " 导入（" + fetched.project().key() + " / " + externalId
                        + "）。任务为默认顺序排期，请补充目标描述与技能需求后再求解", start, end);
        link(client.source(), "PROJECT", externalId, projectId, fetched.project().url());
        var cursor = start;
        int imported = 0, open = 0;
        for (var task : fetched.tasks()) {
            var window = SyncMappers.schedule(cursor, (int) Math.ceil(task.hours() / 8.0));
            long taskId = db.queryForObject("""
                    insert into task(project_id, name, description, priority, estimated_hours, start_date, end_date, status)
                    values (?,?,?,?,?,?,?,?) returning id""", Long.class,
                    projectId, task.name(), task.key(), task.priority(), task.hours(), window[0], window[1], task.status());
            link(client.source(), "TASK", task.externalId(), taskId, task.url());
            cursor = window[1].plusDays(1);
            imported++;
            if (!"DONE".equals(task.status()) && !"CANCELLED".equals(task.status())) open++;
        }
        log.info("project imported from {}, externalId={}, projectId={}, tasks={}", client.source(), externalId, projectId, imported);
        return Map.of("projectId", projectId, "projectName", fetched.project().name(), "startDate", start.toString(), "endDate", end.toString(),
                "tasksImported", imported, "openTasks", open, "doneTasks", imported - open);
    }

    /** 增量刷新：远端新增入列、变化更新、删除取消本地任务；有生效分配时拒绝。 / incremental refresh, blocked while allocations are active. */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refresh(String source, long projectId) {
        var client = client(source);
        var projectLinks = db.queryForList("select external_id from integration_link where source=? and external_type='PROJECT' and internal_id=?",
                String.class, client.source(), projectId);
        if (projectLinks.isEmpty()) bad("该项目不是从 " + client.source() + " 导入的项目");
        if (db.queryForObject("select count(*) from resource_allocation where project_id=? and status in ('PLANNED','CONFIRMED')", Long.class, projectId) > 0)
            bad("项目已有生效分配，请先撤销方案再刷新");
        ProjectWithTasks fetched = client.fetch(projectLinks.getFirst());
        var existing = new HashMap<String, Long>();
        db.query("select external_id, internal_id from integration_link where source=? and external_type='TASK'",
                rs -> { existing.put(rs.getString("external_id"), rs.getLong("internal_id")); }, client.source());
        LocalDate start = LocalDate.now();
        db.update("update project set end_date=greatest(end_date, ?), updated_at=now() where id=?",
                SyncMappers.windowEnd(start, fetched.tasks().stream().map(RemoteTask::hours).toList()), projectId);
        var cursor = start;
        int added = 0, updated = 0, cancelled = 0;
        var seen = new HashSet<String>();
        for (var task : fetched.tasks()) {
            seen.add(task.externalId());
            var window = SyncMappers.schedule(cursor, (int) Math.ceil(task.hours() / 8.0));
            cursor = window[1].plusDays(1);
            var taskId = existing.get(task.externalId());
            if (taskId == null || db.queryForObject("select count(*) from task where id=? and project_id=?", Long.class, taskId, projectId) == 0) {
                long created = db.queryForObject("""
                        insert into task(project_id, name, description, priority, estimated_hours, start_date, end_date, status)
                        values (?,?,?,?,?,?,?,?) returning id""", Long.class,
                        projectId, task.name(), task.key(), task.priority(), task.hours(), window[0], window[1], task.status());
                link(client.source(), "TASK", task.externalId(), created, task.url());
                added++;
            } else {
                db.update("update task set name=?, priority=?, estimated_hours=?, start_date=?, end_date=?, status=?, updated_at=now() where id=?",
                        task.name(), task.priority(), task.hours(), window[0], window[1], task.status(), taskId);
                updated++;
            }
        }
        for (var entry : existing.entrySet()) {
            if (!seen.contains(entry.getKey()) && db.queryForObject("select count(*) from task where id=? and project_id=?", Long.class, entry.getValue(), projectId) > 0) {
                db.update("update task set status='CANCELLED', updated_at=now() where id=?", entry.getValue());
                cancelled++;
            }
        }
        db.update("update integration_link set synced_at=now() where source=? and external_type='PROJECT' and internal_id=?", client.source(), projectId);
        log.info("project refreshed from {}, projectId={}, added={}, updated={}, cancelled={}", client.source(), projectId, added, updated, cancelled);
        return Map.of("projectId", projectId, "remoteTasks", fetched.tasks().size(), "added", added, "updated", updated, "cancelled", cancelled);
    }

    /** 已从该来源导入的项目（含刷新入口所需状态）/ imported projects with refresh-relevant state. */
    public List<Map<String, Object>> imports(String source) {
        var client = client(source);
        return db.queryForList("""
                select l.internal_id "projectId", p.name "projectName", p.status "projectStatus",
                       l.external_id "externalId", l.url, l.synced_at "syncedAt",
                       (select count(*) from task t where t.project_id = l.internal_id and t.status not in ('CANCELLED','DONE')) "openTasks",
                       (select count(*) from resource_allocation a where a.project_id = l.internal_id and a.status in ('PLANNED','CONFIRMED')) "activeAllocations"
                from integration_link l join project p on p.id = l.internal_id
                where l.source = ? and l.external_type = 'PROJECT'
                order by l.internal_id""", client.source());
    }

    /** 已导入项目的映射明细 / mapping detail of an imported project. */
    public List<Map<String, Object>> links(String source, long projectId) {
        var client = client(source);
        return db.queryForList("""
                select l.external_type "externalType", l.external_id "externalId", l.url, l.synced_at "syncedAt",
                       case l.external_type when 'PROJECT' then p.name else t.name end "name"
                from integration_link l
                left join project p on l.external_type='PROJECT' and p.id=l.internal_id
                left join task t on l.external_type='TASK' and t.id=l.internal_id
                where l.source=? and l.internal_id in (select ?::bigint union select id from task where project_id=?)
                order by l.external_type desc, l.internal_id""", client.source(), projectId, projectId);
    }

    private void link(String source, String type, String externalId, long internalId, String url) {
        db.update("insert into integration_link(source, external_type, external_id, internal_id, url) values (?,?,?,?,?)",
                source, type, externalId, internalId, url);
    }

    private ExternalProjectClient client(String source) {
        var key = source == null ? "" : source.toLowerCase();
        var client = clients.get(key);
        if (client == null) bad("不支持的来源：" + key + "，可选 jira / zentao / gitlab");
        if (!client.enabled()) bad("未启用 " + client.source() + " 同步：配置 app.sync." + key + ".enabled=true 及 base-url 与凭证");
        return client;
    }
}
