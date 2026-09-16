package com.company.orchestrator.integration;

import java.util.List;

/**
 * 外部项目源客户端契约：各源实现“列项目 / 取项目与任务”，统一输出 ARO 口径的
 * 状态（TODO / IN_PROGRESS / DONE / CANCELLED）、优先级（1–5）与工时（≥1h）。
 * Contract of an external project source: list projects, fetch one with its
 * tasks; adapters normalize status, priority and hours into ARO terms.
 */
public interface ExternalProjectClient {

    /** 来源标识 / source identifier: JIRA | ZENTAO | GITLAB. */
    String source();

    /** 配置开关 / whether credentials and base-url are configured. */
    boolean enabled();

    /** 列出外部项目（不含过滤）/ list remote projects. */
    List<RemoteProject> listProjects();

    /** 拉取一个项目的任务清单 / fetch one project with its tasks. */
    ProjectWithTasks fetch(String externalId);

    record RemoteProject(String externalId, String key, String name, String status, String url) {}

    record RemoteTask(String externalId, String key, String name, String status, int priority, int hours, String url) {}

    record ProjectWithTasks(RemoteProject project, List<RemoteTask> tasks) {}
}
