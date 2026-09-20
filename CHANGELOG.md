# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.11.0] - 2026-09-20
### Changed
- CI 自动触发已关闭（push 到 main/dev、所有 PR 不再运行）；工作流保留，改为仅手动触发（`workflow_dispatch`）/ CI auto-triggers disabled; workflow kept as manual-only (`workflow_dispatch`).
- `resource_plan.gaps` 迁移为 jsonb，删除从未写入的 `score` 死列，`resource_plan_item` 增加 `(plan_id, task_id)` 唯一索引（`V10` 先去重保最低 id 再建）/ gaps moved to jsonb, the never-written score column dropped, and plan items carry a unique fallback index.
- 外部同步刷新：本地与远端同为终态（DONE/CANCELLED）的任务保留原排期与记录不再重排（远端重开则照常更新）；远端已删仅取消本地未完结任务；任务映射加载收敛到本项目 / refresh freezes tasks closed on both sides, only cancels locally open tasks on remote delete, and scopes the task-map query to the project.
- 版本对齐：pom `0.1.0-SNAPSHOT` → `0.11.0-SNAPSHOT`，与 CHANGELOG 同步；发布时在 main 上打 `v0.X.Y` tag / pom version aligned with the changelog; cut `v0.X.Y` tags on main at release time.

### Added
- AI 审计保留策略：`ai_execution` 超期每日 03:30 清理（`AI_AUDIT_RETENTION_DAYS` 默认 90 天，0 = 永久；cron 经 `app.ai-audit.purge-cron` 可配）/ scheduled audit retention purge.
- 登录失败限速：内存滑动窗口按「用户名|IP」（`LOGIN_MAX_FAILURES` 默认 5、`LOGIN_LOCK_MINUTES` 默认 15，0 = 关闭），锁定期间返回 429 `auth.login.locked` 双语文案 / in-memory login throttling with a bilingual 429.
- 资源排期超过 500 位在职成员时返回 `truncated` 标记，前端显示截断提示（CSV 同口径）/ timeline reports and surfaces truncation beyond 500 employees.

### Fixed
- Unsupported HTTP methods now return 405 `E40500` (`error.method.not.allowed`, bilingual) with an `Allow` header instead of a 500 `E50000` — companion to the 0.7.0 route-404 fix.
- AI 上游调用失败与超时现在记录根因日志（原先 `AI_UPSTREAM_FAILED` 无任何日志，live 模式排障失明）/ upstream AI failures and timeouts now log the root cause.
- `docker-compose.yml` 补齐 `REPLAN_*` / `SKILL_SEMANTIC_*` / `EMBEDDING_MODEL` / `AI_EMBEDDING_PROVIDER` / `SYNC_*`（Jira/GitLab/GitHub/ZenTao）环境变量透传，此前 compose 部署无法启用这些功能 / compose now passes through the replan, semantic-search and external-sync env vars.
- `application.yml` 把禅道 `path-projects`/`path-tasks` 从误放的 github 块移回 zentao 块，`SYNC_ZENTAO_PATH_*` 覆盖恢复生效 / ZenTao path-override keys moved back under the zentao block and work again.
- 删除项目前置检查资源方案/分配并给出业务提示（替代泛化 409），连带清理 `integration_link` 映射；`V9` 一次性清理存量孤儿，同源外部项目可再次导入 / project deletion pre-checks plans/allocations and clears sync links; `V9` purges existing orphans so re-import works.
- 删除员工前置检查关联账号、负责项目与分配记录 / employee deletion pre-checks account, managed projects and booking references.
- 外部同步的 HTTP 拉取移出数据库事务，慢远端不再长期占用连接池；刷新的生效分配守卫移入事务内 / sync fetches run outside the write transaction, while the active-allocation guard moved inside it.
- 周负载预警的 `plan_id` 排除改为 NULL 安全的 `is distinct from`；时间线与预警的“今天”由 Java 统一传入，消除 DB/VM 时区口径差 / NULL-safe plan exclusion and one "today" timezone for timeline and warnings.
- AI 规划流式前端请求加 180s 兜底超时；登录失败展示服务端双语消息；非 JSON 响应给出可读错误 / AI planning stream gets a 180s timeout; login shows the server message; non-JSON responses fail readably.
- `Dockerfile` 的 pnpm 版本恢复由 `web/package.json` 的 `packageManager` 字段钉住（原 11.20.0 与 CI/package.json 的 10.28.2 不一致）/ the Docker build now honors the packageManager pin.
- 出站通知的 afterCommit 回调整体兜底，日志写入失败不再向事务回调抛异常 / the webhook afterCommit callback is fully guarded and can no longer throw.

### Security
- CSV 导出对以 `=` `+` `-` `@` 或制表符开头的单元格前置单引号，阻断 Excel 公式注入（含外部系统导入的名称）/ CSV exports neutralize Excel formula injection by quote-guarding leading `= + - @` and tab cells, including names imported from external systems.
- 简历上传 docx 解压加 1MB 上限（流式读取），阻断 zip 炸弹内存耗尽 / docx decompression is streamed with a 1MB cap, blocking zip-bomb memory exhaustion.
- 会话 Cookie 显式 `SameSite=Lax` / session cookie now sets `SameSite=Lax` explicitly.
- 请求体解析失败不再把 Jackson 解析错误原文回传客户端 / malformed-request responses no longer echo parser internals.

### Added
- Flyway `V8` 热路径索引：`employee_skill(skill_id)`、`resource_allocation(task_id)`、生效分配按 `end_date` 的部分索引、`task_skill_requirement(skill_id)` / hot-path indexes for skill analytics, task close-out, active-booking scans and skill patrols.
- `CandidateServiceTest`：候选容量数学单测（基点单位换算、技能门槛、窗口清零、占用扣减、PREFERRED 评分、周末剔除）/ unit tests for the candidate capacity math.

## [0.10.0] - 2026-09-17
### Added
- GitHub as a fourth sync source (`SYNC_GITHUB_ENABLED` / `SYNC_GITHUB_BASE_URL` / `SYNC_GITHUB_TOKEN`): `/user/repos` lists owned repositories (externalId = `owner/repo`), the issues endpoint is paged at 100 with pull requests (items carrying a `pull_request` field) filtered out, `closed` maps to DONE and issues default to 8h / priority 3. Backed by Flyway `V7` widening the `integration_link.source` check to admit GITHUB; local contract coverage extends `scripts/sync_fixture.py` + `scripts/check_sync_contract.py`.

## [0.9.0] - 2026-09-17
### Added
- CSV exports for management reporting: `GET /api/v1/export/supply-demand` (flat one-row-per-skill summary or weekly one-row-per-skill-week detail), `GET /api/v1/export/timeline` (active bookings) and `GET /api/v1/system/export/ai-log` (AI audit trail, admin-gated) — UTF-8 BOM + RFC4180 quoting for direct Excel use, with export buttons on the capability / timeline / settings tabs.
- `input_hash` is now project-scoped: the four project tables hash only this project's rows while people / skills / global bookings stay whole, so creating tasks in one project no longer invalidates drafts of another (people, availability and cross-project booking changes still do, by design).

## [0.8.0] - 2026-09-16
### Added
- Execution-side lifecycle closes the business loop with guarded status transitions: `POST /api/v1/tasks/{id}/status` (TODO → IN_PROGRESS → DONE / CANCELLED, DONE/CANCELLED terminal; completing or cancelling a task closes its active bookings — allocation status `COMPLETED` / `CANCELLED` — so capacity is released at once across solving, timeline, capability forecasts and cross-project warnings; parents cannot close while children stay open), `POST /api/v1/projects/{id}/status` (PLANNING → IN_PROGRESS → ON_HOLD / COMPLETED / CANCELLED; terminal transitions require no active bookings, completion requires every task closed, terminal projects can no longer be solved) and `POST /api/v1/employees/{id}/status` (ACTIVE ⇄ ON_LEAVE / INACTIVE, INACTIVE only returns to ACTIVE; deactivation drops the employee from candidacy immediately and triggers the replan patrol on booked projects). New webhook events `PLAN_CANCELLED` and `PROJECT_COMPLETED`; the UI gains a task status column with start/complete/cancel actions plus project and employee status pickers.
- External sync now fetches every page (Jira `startAt`, GitLab / ZenTao `limit`+`page`, capped at 20 pages of 100) instead of the first 100–200 items, and a new `GET /api/v1/sync/{source}/imports` lists imported projects with open-task counts, sync times and the active-allocation guard state for the settings-page refresh action.
- Live semantic embeddings are batched (64 per `/v1/embeddings` request) during rebuild; auto replanning now reuses the active plan's solving strategy instead of hardcoding BALANCED.

## [0.7.0] - 2026-09-16
### Added
- Phase 3 remainder: optional pgvector semantic skill search — a second Flyway location (`db/migration-semantic`, V900+) creates `skill_embedding` only when `SKILL_SEMANTIC_ENABLED=true` on a pgvector-enabled PostgreSQL; `local` mode embeds deterministically in-process (char-gram hash, default 256-dim) and `live` mode calls an OpenAI-compatible `/v1/embeddings` endpoint. New `GET /api/v1/skills/semantic` search + `POST /skills/semantic/rebuild`, first-enable auto backfill, and vector suggestions for unmatched names in AI skill extraction (threshold 0.5, layered over the deterministic 0.82 similarity hint).
- Phase 4 remainder: event-driven auto replanning — availability windows, skill-profile writes, skill-library edits and project-window changes now enqueue an after-commit asynchronous patrol of affected active plans (`REPLAN_AUTO_TRIGGER=off|detect|auto`, cooldown `REPLAN_COOLDOWN_MINUTES`). `detect` pushes a `REPLAN_SUGGESTED` webhook (draft plan id included when present); `auto` additionally drafts a replacement plan (BALANCED) that still requires human confirmation. All decisions land in the new `replan_trigger_log` table exposed via `GET /api/v1/replan/triggers`.
- Phase 5 remainder: one-way external project sync for Jira (REST v2), ZenTao (OpenAPI v1, paths overridable) and GitLab (v4) — `GET /api/v1/sync/{source}/projects`, `POST …/import` (tasks land on a default sequential workday schedule with normalized status/priority/hours; skill requirements stay human-authored), `POST …/refresh` (added/updated/cancelled diff, rejected while allocations are active) and `GET …/links`, backed by the new `integration_link` table. Local contract test: `scripts/sync_fixture.py` + `scripts/check_sync_contract.py`.
- Phase 6 remainder: weekly-refined supply model and external market benchmarks — `GET /capability/supply-weekly` (per-week demand/supply/gap, leave and bookings deducted per week) and `supply-demand?model=flat|weekly` (weekly caps surplus weeks so peak shortages surface); admin-imported market data (`POST /capability/market/import`, new `market_skill` table: tightness/salary band/hiring lead time) enriches forecast rows and AI advice as read-only context.

### Fixed
- LLM output parsing now strips reasoning-model `<think>…</think>` blocks (Qwen3 / DeepSeek-R1 style, truncated blocks tolerated) before JSON deserialization; shared `AiText.clean` replaces the duplicated fence-stripping in both AI planning and AI skill extraction.
- Unreadable-body / param-type errors now fill the `请求参数错误：{0}` detail instead of returning a literal `{0}` placeholder; unmatched routes now return 404 `E40400` (`error.not.found`, path included) instead of 400.

## [0.6.1] - 2026-09-15
### Added
- Phase 6 remainder: pipeline scenario simulation (`GET /capability/scenario`, window stretches over queued PLANNING projects, per-skill base/scenario/delta gap people), gap trends across 8/12/26-week windows (`GET /capability/trends`), and AI gap advice (`POST /capability/advise`, deterministic demo text, audited `CAPABILITY_ADVISE`). Refactored the forecast core into shared `gapRows`/`demandRows`; frontend capability tab gains a deep-analysis panel.

## [0.6.0] - 2026-09-15
### Added
- Phase 6 slice: organizational capability analytics — `GET /capability/supply-demand?weeks=` (skill gaps over an 8/12/26-week window with a conservative demand bar, capacity-discounted supply, 0.8 effective-utilization gap people, and hire/reassign advice), `GET /capability/key-people` (bottleneck-skill holders with competing projects), and `GET /capability/leave-impact` (per-task replaceability what-if). New `capability` module (read-only) and a "Capability" frontend tab.
### Fixed
- Lucide icons `Paperclip` / `AlertTriangle` were referenced in templates but never imported (rendered as unknown elements); now imported along with `Compass`.

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

