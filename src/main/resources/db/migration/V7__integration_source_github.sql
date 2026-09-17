-- GitHub 同步来源加入 integration_link.source 白名单 / admit GITHUB as an integration source.
ALTER TABLE integration_link DROP CONSTRAINT ck_integration_source;
ALTER TABLE integration_link ADD CONSTRAINT ck_integration_source CHECK (source IN ('JIRA', 'ZENTAO', 'GITLAB', 'GITHUB'));
