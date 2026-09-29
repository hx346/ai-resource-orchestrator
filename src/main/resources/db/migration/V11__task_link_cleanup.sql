-- 清理 V9 之后新增的孤儿 TASK 映射（任务被单独删除时遗留）：
-- 唯一约束 (source, external_type, external_id) 会使 refresh 把该远端任务
-- 当作新任务插入时撞约束，永久阻断增量刷新。
-- 此后 TaskService.delete 已连带清理；本迁移一次性修复存量。
-- Purge orphaned TASK links left behind by individual task deletions since V9:
-- the unique constraint otherwise makes refresh fail when it re-inserts the
-- same external task. TaskService.delete now clears links inline; this
-- migration repairs existing rows once.
DELETE FROM integration_link l
 WHERE l.external_type = 'TASK'
   AND NOT EXISTS (SELECT 1 FROM task t WHERE t.id = l.internal_id);
