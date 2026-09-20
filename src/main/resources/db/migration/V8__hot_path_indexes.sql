-- 热路径索引（CR 发现 M7）：能力分析逐技能查询、任务收尾与冲突查询、
-- 时间线/周负载预警扫描、技能事件巡检此前均为顺序扫描。
-- Hot-path indexes: per-skill analytics, task close-out and conflict queries,
-- timeline / weekly-load scans, and skill-event patrols previously seq-scanned.
CREATE INDEX idx_employee_skill_skill ON employee_skill (skill_id);
CREATE INDEX idx_allocation_task ON resource_allocation (task_id);
CREATE INDEX idx_allocation_active_end ON resource_allocation (end_date) WHERE status IN ('PLANNED', 'CONFIRMED');
CREATE INDEX idx_tsr_skill ON task_skill_requirement (skill_id);
