-- 外部市场技能参考数据（Phase 6 余项）：由管理员导入（招聘平台 / 薪酬报告等外部来源），
-- 只读叠加到供需预测与 AI 建议上，不参与任何求解。
-- External market benchmarks per skill (Phase 6 remainder): imported by admins
-- from external sources (hiring platforms, salary reports); read-only context
-- for the gap forecast and AI advice, never used by the solver.
CREATE TABLE market_skill (
    skill_id          BIGINT PRIMARY KEY REFERENCES skill(id) ON DELETE CASCADE,
    source            VARCHAR(64)  NOT NULL DEFAULT 'manual',
    demand_index      NUMERIC(5,2) NOT NULL CHECK (demand_index >= 0 AND demand_index <= 100),
    salary_min        NUMERIC(12,2),
    salary_max        NUMERIC(12,2),
    hiring_lead_weeks INT          CHECK (hiring_lead_weeks IS NULL OR hiring_lead_weeks BETWEEN 0 AND 104),
    note              VARCHAR(512),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE market_skill IS '技能市场参考数据（外部导入）/ External market benchmarks per skill';
COMMENT ON COLUMN market_skill.demand_index IS '市场紧张度 0–100（越高越难招）/ Market tightness 0-100';
