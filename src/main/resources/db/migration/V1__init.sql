-- =====================================================================
-- AI Resource Orchestrator - Initial schema (Phase 1 MVP)
-- Dialect: PostgreSQL 16+
-- Managed by Flyway. Never edit applied migrations; add new V*.sql instead.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Organizations & people
-- ---------------------------------------------------------------------

CREATE TABLE sys_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    employee_id   BIGINT,
    role          VARCHAR(32)  NOT NULL DEFAULT 'EMPLOYEE',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT ck_sys_user_role CHECK (role IN ('ADMIN', 'PROJECT_MANAGER', 'DEPARTMENT_MANAGER', 'EMPLOYEE')),
    CONSTRAINT ck_sys_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE sys_user IS '系统用户与角色 / System users and roles';

CREATE TABLE department (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT,
    name        VARCHAR(128) NOT NULL,
    code        VARCHAR(64),
    description VARCHAR(512),
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE department IS '部门 / Departments';

CREATE TABLE employee (
    id               BIGSERIAL PRIMARY KEY,
    employee_no      VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    department_id    BIGINT       NOT NULL,
    position         VARCHAR(128),
    location         VARCHAR(128),
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    weekly_hours     INT          NOT NULL DEFAULT 40,
    default_capacity NUMERIC(5,2) NOT NULL DEFAULT 100.00,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_employee_no UNIQUE (employee_no),
    CONSTRAINT ck_employee_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ON_LEAVE')),
    CONSTRAINT ck_employee_capacity CHECK (default_capacity >= 0 AND default_capacity <= 100)
);

COMMENT ON TABLE employee IS '员工 / Employees';

-- ---------------------------------------------------------------------
-- Skill library
-- ---------------------------------------------------------------------

CREATE TABLE skill_category (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    code        VARCHAR(64),
    sort_order  INT          NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE skill_category IS '技能分类 / Skill categories';

CREATE TABLE skill (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT       NOT NULL,
    parent_id   BIGINT,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_skill_name UNIQUE (name)
);

COMMENT ON TABLE skill IS '技能（树形结构，支持层级）/ Skills (tree structure)';

CREATE TABLE skill_alias (
    id         BIGSERIAL PRIMARY KEY,
    skill_id   BIGINT       NOT NULL,
    alias      VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE skill_alias IS '技能别名，用于归一化 / Skill aliases for normalization';

CREATE UNIQUE INDEX uk_skill_alias ON skill_alias (lower(alias));

CREATE TABLE employee_skill (
    id               BIGSERIAL PRIMARY KEY,
    employee_id      BIGINT       NOT NULL,
    skill_id         BIGINT       NOT NULL,
    level            SMALLINT     NOT NULL,
    experience_months INT         NOT NULL DEFAULT 0,
    source           VARCHAR(32)  NOT NULL DEFAULT 'SELF',
    confidence       NUMERIC(4,3) NOT NULL DEFAULT 1.000,
    verified         BOOLEAN      NOT NULL DEFAULT FALSE,
    last_used_at     DATE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_employee_skill UNIQUE (employee_id, skill_id),
    CONSTRAINT ck_employee_skill_level CHECK (level BETWEEN 1 AND 5),
    CONSTRAINT ck_employee_skill_source CHECK (source IN ('SELF', 'MANAGER', 'PROJECT', 'RESUME', 'AI', 'CERTIFICATE')),
    CONSTRAINT ck_employee_skill_confidence CHECK (confidence >= 0 AND confidence <= 1)
);

COMMENT ON TABLE employee_skill IS '员工技能画像，L1~L5 / Employee skill profile';

-- ---------------------------------------------------------------------
-- Project & tasks
-- ---------------------------------------------------------------------

CREATE TABLE project (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(256) NOT NULL,
    description TEXT,
    priority    SMALLINT     NOT NULL DEFAULT 3,
    status      VARCHAR(32)  NOT NULL DEFAULT 'PLANNING',
    start_date  DATE,
    end_date    DATE,
    manager_id  BIGINT,
    location    VARCHAR(128),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_project_priority CHECK (priority BETWEEN 1 AND 5),
    CONSTRAINT ck_project_status CHECK (status IN ('PLANNING', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_project_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

COMMENT ON TABLE project IS '项目 / Projects';

CREATE TABLE project_milestone (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    name        VARCHAR(256) NOT NULL,
    due_date    DATE,
    status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_milestone_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'SKIPPED'))
);

COMMENT ON TABLE project_milestone IS '项目里程碑 / Project milestones';

CREATE TABLE task (
    id               BIGSERIAL PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    parent_id        BIGINT,
    milestone_id     BIGINT,
    name             VARCHAR(256) NOT NULL,
    description      TEXT,
    priority         SMALLINT     NOT NULL DEFAULT 3,
    estimated_hours  INT,
    start_date       DATE,
    end_date         DATE,
    status           VARCHAR(32)  NOT NULL DEFAULT 'TODO',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_task_priority CHECK (priority BETWEEN 1 AND 5),
    CONSTRAINT ck_task_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_task_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

COMMENT ON TABLE task IS '任务（支持 WBS 层级）/ Tasks (WBS hierarchy)';

CREATE INDEX idx_task_project ON task (project_id);

CREATE TABLE task_dependency (
    id                 BIGSERIAL PRIMARY KEY,
    predecessor_task_id BIGINT NOT NULL,
    successor_task_id   BIGINT NOT NULL,
    dependency_type     VARCHAR(8) NOT NULL DEFAULT 'FS',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_task_dependency UNIQUE (predecessor_task_id, successor_task_id),
    CONSTRAINT ck_task_dependency_type CHECK (dependency_type IN ('FS', 'SS', 'FF', 'SF')),
    CONSTRAINT ck_task_dependency_no_self CHECK (predecessor_task_id <> successor_task_id)
);

COMMENT ON TABLE task_dependency IS '任务依赖 / Task dependencies (FS/SS/FF/SF)';

CREATE TABLE task_skill_requirement (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT       NOT NULL,
    skill_id        BIGINT       NOT NULL,
    min_level       SMALLINT     NOT NULL,
    weight          NUMERIC(5,4) NOT NULL DEFAULT 1.0,
    requirement_type VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_task_skill_requirement UNIQUE (task_id, skill_id),
    CONSTRAINT ck_tsr_min_level CHECK (min_level BETWEEN 1 AND 5),
    CONSTRAINT ck_tsr_weight CHECK (weight >= 0 AND weight <= 1),
    CONSTRAINT ck_tsr_type CHECK (requirement_type IN ('REQUIRED', 'PREFERRED', 'OPTIONAL'))
);

COMMENT ON TABLE task_skill_requirement IS '任务技能需求 / Task skill requirements';

-- ---------------------------------------------------------------------
-- Availability & resource plans
-- ---------------------------------------------------------------------

CREATE TABLE employee_availability (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT       NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    capacity    NUMERIC(5,2) NOT NULL,
    type        VARCHAR(32)  NOT NULL DEFAULT 'AVAILABLE',
    remark      VARCHAR(512),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_availability_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_availability_capacity CHECK (capacity >= 0 AND capacity <= 100),
    CONSTRAINT ck_availability_type CHECK (type IN ('AVAILABLE', 'LEAVE', 'TRAINING', 'BUSINESS_TRIP', 'BLOCKED'))
);

COMMENT ON TABLE employee_availability IS '员工可用时间 / Employee availability windows';

CREATE INDEX idx_availability_employee ON employee_availability (employee_id, start_date, end_date);

CREATE TABLE resource_plan (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    version         INT          NOT NULL DEFAULT 1,
    strategy        VARCHAR(32)  NOT NULL DEFAULT 'BALANCED',
    score           NUMERIC(12,4),
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    solver_duration BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_resource_plan_version UNIQUE (project_id, version),
    CONSTRAINT ck_resource_plan_strategy CHECK (strategy IN ('BALANCED', 'FASTEST', 'LOWEST_COST', 'LOWEST_RISK', 'BEST_SKILL_MATCH')),
    CONSTRAINT ck_resource_plan_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'REJECTED', 'ARCHIVED'))
);

COMMENT ON TABLE resource_plan IS 'Solver 生成的资源方案（多版本）/ Resource plans generated by the solver';

CREATE TABLE resource_plan_item (
    id          BIGSERIAL PRIMARY KEY,
    plan_id     BIGINT       NOT NULL,
    project_id  BIGINT       NOT NULL,
    task_id     BIGINT       NOT NULL,
    employee_id BIGINT       NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    allocation  NUMERIC(5,2) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_plan_item_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_plan_item_allocation CHECK (allocation > 0 AND allocation <= 100)
);

COMMENT ON TABLE resource_plan_item IS '资源方案明细 / Resource plan line items';

CREATE INDEX idx_plan_item_plan ON resource_plan_item (plan_id);
CREATE INDEX idx_plan_item_employee ON resource_plan_item (employee_id);

CREATE TABLE resource_allocation (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    task_id     BIGINT       NOT NULL,
    employee_id BIGINT       NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    allocation  NUMERIC(5,2) NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'PLANNED',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_allocation_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_allocation_range CHECK (allocation > 0 AND allocation <= 100),
    CONSTRAINT ck_allocation_status CHECK (status IN ('PLANNED', 'CONFIRMED', 'COMPLETED', 'CANCELLED'))
);

COMMENT ON TABLE resource_allocation IS '人工确认后的正式资源分配 / Confirmed resource allocations';

CREATE INDEX idx_allocation_employee ON resource_allocation (employee_id, start_date, end_date);
CREATE INDEX idx_allocation_project ON resource_allocation (project_id);

-- ---------------------------------------------------------------------
-- AI execution audit
-- ---------------------------------------------------------------------

CREATE TABLE ai_execution (
    id             BIGSERIAL PRIMARY KEY,
    type           VARCHAR(64)  NOT NULL,
    business_id    VARCHAR(128),
    model          VARCHAR(128),
    prompt_version VARCHAR(64),
    input          TEXT,
    output         TEXT,
    status         VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS',
    duration       BIGINT,
    token_usage    JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_execution_status CHECK (status IN ('SUCCESS', 'FAILED', 'TIMEOUT'))
);

COMMENT ON TABLE ai_execution IS 'AI 调用审计：排查、Prompt 版本、成本、追踪 / AI call audit trail';

CREATE INDEX idx_ai_execution_type ON ai_execution (type, business_id);
