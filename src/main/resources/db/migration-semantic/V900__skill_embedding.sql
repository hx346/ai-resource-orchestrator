-- 可选能力：pgvector 技能语义检索（Phase 3 余项）。
-- 仅当 app.skill-semantic.enabled=true（SKILL_SEMANTIC_ENABLED）时由 FlywayConfig
-- 追加本目录加载，需要 PostgreSQL 已安装 pgvector（镜像 pgvector/pgvector:pg16）。
-- 版本号从 900 起，避开主迁移序列，后启用也不会触发 out-of-order。
-- Optional pgvector semantic skill search (Phase 3 remainder). This location is
-- only added to Flyway when app.skill-semantic.enabled=true and requires the
-- pgvector extension binary. Versions start at 900 to stay above the mainline
-- so enabling later never needs out-of-order migration.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE skill_embedding (
    skill_id   BIGINT PRIMARY KEY REFERENCES skill(id) ON DELETE CASCADE,
    model      VARCHAR(64) NOT NULL,
    dim        INT         NOT NULL,
    embedding  vector      NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE skill_embedding IS '技能语义向量（可选 pgvector 能力）/ Semantic skill vectors (optional pgvector)';
-- 列不固定维度：local(默认 256) 与 live 两种嵌入模式可共存切换（重建后生效）。
-- 技能库到万级再考虑定维列 + HNSW 索引；当前量级顺序扫描足够。
-- Dimensionless column: local (default 256-dim hash) and live embeddings can be
-- swapped by rebuilding; add a typed column + HNSW index at ~10k skills scale.
