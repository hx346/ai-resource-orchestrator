-- 出站通知记录（企业微信群机器人 / 飞书 / 钉钉 webhook 等）
-- Outbound notification log (WeCom / Feishu / DingTalk group-bot webhooks, generic JSON).
CREATE TABLE notification_log (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(64)  NOT NULL,
    provider    VARCHAR(32)  NOT NULL,
    target      VARCHAR(512) NOT NULL,
    payload     TEXT         NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    error       VARCHAR(512),
    duration    INT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

COMMENT ON TABLE notification_log IS '出站通知记录 / Outbound notification log';
COMMENT ON COLUMN notification_log.type IS '事件类型：PLAN_CONFIRMED / AVAILABILITY_CONFLICT / ... / Event type';
COMMENT ON COLUMN notification_log.target IS '脱敏后的 webhook 地址（去除 query 中的令牌）/ masked webhook URL';
