-- 数据层收敛（CR 收尾）：
-- 1) gaps 转 jsonb（可校验、可用 jsonb 运算符）；2) 删除从未写入的 score 死列；
-- 3) resource_plan_item 每方案每任务唯一（应用层每任务一条，此处 DB 兜底，先去重保最低 id）；
-- 4) ai_execution(created_at) 索引支撑每日保留清理。
-- Schema cleanup: gaps to jsonb, drop the never-written score column, a unique
-- fallback index for plan items (dedupe keeps the lowest id), and an index
-- backing the daily ai_execution retention purge.
-- 先去默认值再转 jsonb，否则 PG 拒绝（text 字面量默认无法自动转 jsonb）/ drop the default first: PG cannot auto-cast the '[]' text default
ALTER TABLE resource_plan ALTER COLUMN gaps DROP DEFAULT;
ALTER TABLE resource_plan ALTER COLUMN gaps TYPE jsonb USING gaps::jsonb;
ALTER TABLE resource_plan ALTER COLUMN gaps SET DEFAULT '[]'::jsonb;

ALTER TABLE resource_plan DROP COLUMN score;

DELETE FROM resource_plan_item a USING resource_plan_item b
 WHERE a.plan_id = b.plan_id AND a.task_id = b.task_id AND a.id > b.id;
CREATE UNIQUE INDEX uk_plan_item_plan_task ON resource_plan_item (plan_id, task_id);

CREATE INDEX idx_ai_execution_created ON ai_execution (created_at);
