/**
 * 外部项目同步（Phase 5 余项）：Jira / 禅道 / GitLab 的项目与任务单向导入为 ARO
 * 项目任务（integration_link 维护内外映射），在无生效分配时可增量刷新。技能需求
 * 不随导入生成，由用户编辑补充——保持“AI / 集成只搬运事实，人负责目标与约束”的分工。
 * External project sync (Phase 5 remainder): one-way import of Jira / ZenTao /
 * GitLab projects and issues into ARO tasks, mapped via integration_link, with
 * incremental refresh while no allocation set is active. Skill requirements are
 * not imported — humans own goals and constraints.
 */
package com.company.orchestrator.integration;
