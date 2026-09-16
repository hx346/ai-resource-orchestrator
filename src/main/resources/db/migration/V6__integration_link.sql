-- 外部项目同步映射（Phase 5 余项）：Jira / 禅道 / GitLab 单向导入的内外对象映射。
-- External sync mapping (Phase 5 remainder): links between imported ARO objects
-- and their Jira / ZenTao / GitLab counterparts.
CREATE TABLE integration_link (
    id            BIGSERIAL PRIMARY KEY,
    source        VARCHAR(32)  NOT NULL,
    external_type VARCHAR(16)  NOT NULL,
    external_id   VARCHAR(128) NOT NULL,
    internal_id   BIGINT       NOT NULL,
    url           VARCHAR(512),
    synced_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_integration_link UNIQUE (source, external_type, external_id),
    CONSTRAINT ck_integration_source CHECK (source IN ('JIRA', 'ZENTAO', 'GITLAB')),
    CONSTRAINT ck_integration_type CHECK (external_type IN ('PROJECT', 'TASK'))
);

COMMENT ON TABLE integration_link IS '外部同步对象映射 / External sync object mapping';
COMMENT ON COLUMN integration_link.external_id IS '外部系统中的项目 / 任务 ID / Id in the external system';

CREATE INDEX idx_integration_internal ON integration_link (source, internal_id);
