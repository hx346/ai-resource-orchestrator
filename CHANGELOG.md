# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Repository initialization: bilingual README (`README.md` / `README.en.md`), MIT License.
- Open-source community files: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, issue & PR templates.
- Backend skeleton: Spring Boot modular monolith (`com.company.orchestrator`) with module packages (auth / employee / department / skill / project / task / allocation / solver / ai / system / common).
- Initial database schema via Flyway (`V1__init.sql`, PostgreSQL dialect) covering employee, skill, project, task, availability, resource plan, and AI execution tables.
- Docker deployment: `Dockerfile` + `docker-compose.yml` (app + PostgreSQL).

### Added (backend MVP phase 1 / 后端 MVP 第一批)

- i18n: bilingual backend messages (zh-CN / en-US) resolved from `Accept-Language`, default Simplified Chinese / 后端提示与错误消息双语，按请求头切换，默认简体中文.
- Unified `Result{code, message, data, traceId}` envelope, global exception handler with business error codes / 统一响应与全局异常处理（业务错误码）.
- Employee module APIs: departments, employees (keyword/department paging), availability windows / 员工、部门、可用性接口.
- Skill module APIs: skill categories, skills, aliases, employee skill profiles (batch replace), and skill name normalization (`GET /api/v1/skills/normalize`) / 技能库与归一化接口.
- Project module APIs: projects, milestones, tasks (WBS), task dependencies, task skill requirements / 项目、里程碑、任务、依赖、技能需求接口.
- Infrastructure: OpenAPI 3 + Swagger UI (`/swagger-ui.html`), MyBatis-Plus pagination + audit field auto-fill, permissive security & CORS for MVP development / 基础设施配置.
- Unit tests for `SkillNormalizer` (exact match / alias match / miss / blank) / 归一化单元测试.

### Added (MVP loop / MVP 闭环)

- Auth: same-origin form-login sessions, CSRF token endpoint (`GET /api/v1/auth/csrf`), four roles (ADMIN / PROJECT_MANAGER / DEPARTMENT_MANAGER / EMPLOYEE); `ADMIN_PASSWORD` bootstraps `admin` on first startup / 会话登录、CSRF、四角色权限边界、管理员引导.
- AI planning: `demo` / `live` modes, SSE streaming plan generation with strict `PlanDraft` validation (dates within project window, skill IDs, dependency ordering), atomic accept-only-once import, AI review explains a solved plan without modifying it, all LLM I/O audited into `ai_execution` / AI 规划生成、流式输出、草稿校验、原子导入、方案解释与全量审计.
- Solver: Timefold resource planning over a validated snapshot (leaf tasks 1–200, Mon–Fri workdays, span ≤ 2 years, FS/SS/FF/SF dependency checks) with SHA-256 `input_hash` across 9 tables; candidates filtered by skill level / capacity / availability; hard daily-capacity constraints, soft skill-match rewards; infeasible work left as explicit gaps instead of overbooking / 求解快照、候选筛选、硬软约束、缺口不超配.
- Resource plan lifecycle: versioned `solve` (row-locked), `edit` (manual reassignment with over-allocation re-check), `confirm` (input freshness re-check + table lock + copy into `resource_allocation`, idempotent and concurrency-safe), `cancel` / 方案版本化与完整生命周期.
- Frontend MVP: Vue 3 + TS SPA (`App.vue` tab views, `EditDialog` forms), CSRF-aware fetch and SSE parsing in `api.ts`, zh-CN locale / 前端最小闭环.
- Ops & tests: Dockerfile + docker-compose (app + PostgreSQL), Playwright E2E workflow spec, API smoke suite (`scripts/smoke.py`, demo mode) and LLM contract test (`scripts/llm_fixture.py` + `scripts/check_llm_contract.py`) / 部署与测试脚本.

