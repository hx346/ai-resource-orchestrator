# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-09-15
### Added
- Phase 5 slice: outbound webhook notifications — `NotifyService` sends `PLAN_CONFIRMED` and `AVAILABILITY_CONFLICT` events after transaction commit to a Feishu / DingTalk / WeCom / generic group-bot webhook (`NOTIFY_MODE`, `NOTIFY_PROVIDER`, `NOTIFY_WEBHOOK_URL`; failures logged, never thrown). New `notification_log` table (Flyway V3, masked targets), admin endpoints `GET /system/notify/status|log`, a settings-page integration card, a provider-format unit suite, and a local contract test (`scripts/notify_fixture.py` + `scripts/check_notify_contract.py`).

## [0.4.1] - 2026-09-15
### Added
- Phase 4 remainder: org-wide replan patrol (`GET /replan/alerts`, reuses the five conflict checks; nav badge and project-list banner, refreshed right after saving availability) and AI replan diff explanations (`POST /resource-plans/{id}/explain-diff`, deterministic in demo mode, audit type `REPLAN_EXPLAIN`).

## [0.4.0] - 2026-09-15
### Added
- Phase 3 remainder: resume file upload (`POST /employees/{id}/skills/ai-extract-file`, txt/md/docx/pdf via PDFBox + in-code docx unzip), deterministic similarity suggestions for unmatched skill names (`SkillSimilarity`, threshold 0.82), and automatic skill-update suggestions — evidence reports profile vs history-suggested levels with one-click adoption (`source=PROJECT`).
- Phase 4 slice: dynamic replanning — `GET /projects/{id}/replan/impact` (five concrete conflict types), `POST /projects/{id}/replan` re-solves with the project's own bookings excluded, and `confirm()` atomically archives the previous active allocation set. Plain `solve` is rejected while allocations are active. Frontend plan panel gains impact-analysis and re-solve actions; E2E covers the leave → impact → replan → swap loop.
### Changed
- `pom.xml` adds `org.apache.pdfbox:pdfbox` 3.0.5 (resume PDF text extraction); multipart limits set to 5MB/6MB.

## [0.1.0] - 2026-09-14

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

## [0.2.0] - 2026-09-14

### Added (Phase 2 / 第二阶段)

- Skill-level gap analysis: plan gaps now distinguish a true skill gap (no active employee reaches the required level of a REQUIRED skill) from a capacity/time conflict; per-task `missingSkills` and an aggregated `gapSummary` (skill, required level, task count, total hours, window, workdays) are returned with plan details and rendered in the frontend / 技能级缺口分析：区分技能缺口与时间容量冲突，输出缺失技能与聚合汇总。
- Smoke suite covers the new gap classification and aggregation / 冒烟测试覆盖缺口分类与聚合.
- Dev: Vite dev proxy target configurable via `BACKEND_ORIGIN` / 前端开发代理目标支持环境变量.
- Team capacity timeline: `GET /api/v1/allocations/timeline` aggregates PLANNED/CONFIRMED bookings into Monday-based weeks (27-week horizon) for every active employee; the frontend adds a 资源排期 tab with a weekly load heatmap and per-week booking tooltips / 全员周负荷时间线与热力图.
- Tests: E2E scenario covering gap handling (an unassignable task blocks confirmation) and timeline rendering; smoke suite checks the timeline endpoint / 缺口与排期的端到端测试覆盖.
- Solving strategies: `BALANCED` / `BEST_SKILL_MATCH` (skill-match weight ×4) / `LOWEST_RISK` (soft penalty above 60% per-person load concentration); strategies are validated (unknown → 400), persisted on the plan, and selectable in the solve form / 多策略求解.
- Plan comparison: `GET /api/v1/resource-plans/compare?left=&right=` returns a per-task assignment diff plus score/gap summary for two plans of the same project; the frontend renders them side by side with changed rows highlighted / 方案并排对比.
- Project Gantt: the project detail page renders a day-granularity task Gantt (sticky task names, weekend shading, priority-coloured bars, milestone diamonds; capped at 366 columns with a hint) / 项目甘特图.
- Brand favicon (`web/public/favicon.svg`) with an explicit link in `index.html` / 站点图标.
- Cross-project load warnings: plan details include `warnings` listing employees whose weekly load — this plan's items plus other projects' active allocations — reaches 80% or more; the warning block is rendered in the plan panel and feeds the AI review input as risk context / 跨项目周负载预警.
- Docker build: optional `MAVEN_MIRROR_URL` build arg (Dockerfile + compose + `.env.example`) for networks where the build container cannot reach Maven Central reliably / 构建期 Maven 镜像可配置.

