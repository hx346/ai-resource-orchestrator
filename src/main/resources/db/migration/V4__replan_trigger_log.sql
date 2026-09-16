-- 事件自动触发重规划（Phase 4 余项）：基础数据变化提交后异步巡检生效方案的审计记录。
-- Event-driven replanning (Phase 4 remainder): audit of asynchronous post-commit
-- patrols over active plans after base-data changes.
CREATE TABLE replan_trigger_log (
    id            BIGSERIAL PRIMARY KEY,
    source_event  VARCHAR(64)  NOT NULL,
    employee_id   BIGINT,
    project_id    BIGINT       NOT NULL,
    plan_id       BIGINT,
    conflict_count INT         NOT NULL DEFAULT 0,
    action        VARCHAR(32)  NOT NULL,
    detail        VARCHAR(512),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_replan_action CHECK (action IN ('NOTIFIED', 'DRAFTED', 'SKIPPED_DRAFT', 'SKIPPED_COOLDOWN', 'FAILED'))
);

COMMENT ON TABLE replan_trigger_log IS '自动重规划触发审计 / Auto-replan trigger audit';
COMMENT ON COLUMN replan_trigger_log.source_event IS '触发事件：AVAILABILITY_CHANGED / SKILL_PROFILE_CHANGED / SKILL_CHANGED / PROJECT_UPDATED / Triggering event';
COMMENT ON COLUMN replan_trigger_log.action IS '动作：NOTIFIED 已提醒 / DRAFTED 已生成草稿 / SKIPPED_* 跳过 / FAILED 失败';

CREATE INDEX idx_replan_trigger_plan ON replan_trigger_log (plan_id, created_at DESC);
