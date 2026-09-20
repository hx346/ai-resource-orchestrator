-- 清理历史孤儿映射（CR 发现 M8）：项目删除后遗留的 integration_link
-- 因唯一约束 (source, external_type, external_id) 会永久阻断同源重导入。
-- 此后 ProjectService.delete 已连带清理；本迁移一次性修复存量。
-- Purge orphaned sync links left behind by past project deletions; the unique
-- constraint otherwise blocks re-importing the same external project forever.
-- ProjectService.delete now clears links inline; this migration repairs existing rows once.
DELETE FROM integration_link l
 WHERE l.external_type = 'PROJECT'
   AND NOT EXISTS (SELECT 1 FROM project p WHERE p.id = l.internal_id);

DELETE FROM integration_link l
 WHERE l.external_type = 'TASK'
   AND NOT EXISTS (SELECT 1 FROM task t WHERE t.id = l.internal_id);
