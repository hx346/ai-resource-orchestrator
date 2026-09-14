# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AI Resource Orchestrator — AI-powered project capability planning & workforce orchestration platform. Tell the system what the project is; it generates the WBS, matches employee skills, and solves resource allocation. Core division of labor (never violate):

- **LLM (AI module)**: understand, generate, extract, explain only — no persistence, no resource decisions
- **Rules/queries (CandidateService)**: filter candidates by skill level, capacity, availability
- **Timefold Solver**: the actual optimization under hard/soft constraints
- **Human**: confirms plans before allocations take effect

Deliberately a **modular monolith** — no Spring Cloud, Redis, MQ, Elasticsearch, or Kubernetes in the MVP. Do not introduce them.

## Commands

```bash
# Backend (requires JDK 25, PostgreSQL 16+; Flyway auto-migrates schema)
./mvnw spring-boot:run            # http://localhost:8080, Swagger at /swagger-ui.html
./mvnw test                       # all backend tests
./mvnw test -Dtest=ResourceSolverTest                       # one class
./mvnw test -Dtest=ResourceSolverTest#timefoldLeavesGapInsteadOfOverbooking   # one method

# Frontend (web/, uses pnpm)
cd web && pnpm install
pnpm dev                          # http://127.0.0.1:5173, proxies /api -> 127.0.0.1:8080
pnpm build                        # vue-tsc --noEmit && vite build (typecheck included)

# E2E (Playwright; backend + pnpm dev must be running, or set ARO_WEB_URL)
cd web && pnpm test:e2e

# Full stack
cp .env.example .env              # set ADMIN_PASSWORD (>=12 chars, required on first start)
docker compose up -d              # app + postgres; image builds web into the jar's static resources

# Smoke / contract tests against a running instance (Python stdlib only)
ARO_ADMIN_PASSWORD=... python scripts/smoke.py
# LLM contract test: run scripts/llm_fixture.py (local OpenAI-compatible stub),
# point AI_BASE_URL at it with AI_MODE=live, then scripts/check_llm_contract.py
```

## Architecture

Backend: Java 25, Spring Boot 3.5, MyBatis-Plus + JdbcTemplate, Flyway (PostgreSQL only), Spring Security (session + CSRF), Spring AI (OpenAI-compatible), Timefold Solver, Caffeine. Frontend: `web/` — Vue 3 + TS + Vite SPA.

### Modules (`src/main/java/com/company/orchestrator/`)

`auth`, `employee`, `skill`, `project`, `allocation`, `solver`, `ai`, `system`, `common`. CRUD modules follow `controller/dto/entity/mapper/service`; `solver`/`allocation`/`system` are flatter and use raw `JdbcTemplate`.

- Cross-module dependency inversion: `skill/api/SkillUsagePort` is implemented by project's `TaskSkillUsageAdapter` to avoid skill ↔ project cycles. Follow this port pattern for new cross-module queries.
- `common`: `Result<T>` envelope (`Result.ok(...)` from every controller), `ErrorCode` + `BusinessException` + `GlobalExceptionHandler`, shared enums (AvailabilityType, DependencyType, RequirementType, SkillSource).

### The planning → allocation pipeline

1. `AiPlanningService.generate(projectId)` — builds a `PlanDraft` (JSON with tasks, skills, predecessor indexes). In `demo` mode returns a fixed template; in `live` mode calls `AiClient` (Spring AI ChatModel, temp 0.2, 75s timeout, max 4 concurrent). Output is stripped of code fences, deserialized, then **validated** (`validate()`: dates inside project window, skill IDs exist, no duplicates, predecessor ordering strictly increasing) before the client ever sees it. Only `accept()` persists — and only when the project has no tasks yet.
2. `PlanningRepository.load(projectId)` — assembles the solve snapshot: leaf tasks only (1–200), Mon–Fri workdays, project span ≤ 2 years, dependency types FS/SS/FF/SF validated against dates. Also computes `input_hash`: SHA-256 over 9 tables' rows — the optimistic-freshness guard.
3. `ResourcePlanService.solve()` — `select ... for update` on the project row, builds per-request `SolverConfig` (strategy `BALANCED`/`BEST_SKILL_MATCH`/`LOWEST_RISK` selects soft-constraint `Weights`; 2s spent limit; Timefold autoconfiguration is **excluded** in application.yml), then persists `resource_plan` (version++, score, input_hash, gaps JSON) + `resource_plan_item`.
4. Plan lifecycle: DRAFT → `edit()` (manual reassignment; re-checks over-allocation via `ResourceConstraints.excess`) → `confirm()` (re-checks input_hash, no gaps, no conflicting allocations; `LOCK TABLE ... IN SHARE ROW EXCLUSIVE MODE`; copies items into `resource_allocation`) → `cancel()`. Only one active allocation set per project.
5. `AiPlanningService.review(planId)` — AI explains an already-solved plan; never modifies it.

### Constraints (`ResourceConstraints`)

Hard: daily capacity per employee (allocation% × workdays vs. weekly_hours, availability windows, existing PLANNED/CONFIRMED bookings). Soft: unassigned work penalized by priority; skill-match score rewarded. Unsatisfiable tasks become **gaps** rather than overbooked plans.

### Auth

Same-origin form-login sessions; every non-GET API call needs the CSRF token from `GET /api/v1/auth/csrf`. Four roles: ADMIN, PROJECT_MANAGER, DEPARTMENT_MANAGER, EMPLOYEE (org-wide, not per-department). `ADMIN_PASSWORD` env bootstraps `admin` on first startup via `ApplicationRunner` — never resets an existing password.

### AI modes

`app.ai-mode`: `off` (no AI; CRUD + solver still work) / `demo` (deterministic templates, no model calls) / `live` (requires `AI_CHAT_PROVIDER=openai` + `AI_API_KEY`; any OpenAI-compatible base URL). `AiClient.complete()` throws typed `BusinessException`s (AI_NOT_ENABLED / AI_NO_PROVIDER / AI_BUSY / AI_TIMEOUT / ...). All LLM I/O is recorded by `AiAuditService` into `ai_execution`.

### Frontend

Deliberately minimal: `App.vue` is the whole app (tab views: projects/employees/skills/timeline/settings; project detail drives the workflow — task list plus a day-granularity CSS Gantt, the timeline tab renders `GET /api/v1/allocations/timeline` as a weekly capacity heatmap), `components/EditDialog.vue` for all forms, `api.ts` for CSRF + fetch + SSE parsing (`streamPlan` handles `progress`/`result`/`error` events). Sends `Accept-Language: zh-CN`.

## Conventions

- **Style**: the Java code is intentionally compact (single-line guards, `if(x) bad("...")`). Match it. Business error messages are Simplified Chinese inline strings; auth/security messages come from `messages*.properties` (zh-CN default, en-US). Code comments are bilingual zh/en.
- **Static idiom**: `PlanningRepository.bad(message)` throws `BusinessException(BAD_REQUEST, ...)` and is statically imported by other services.
- **Commits**: conventional (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`); branch from `main` as `feat/...`.
- **Database**: PostgreSQL dialect only; every schema change is a new Flyway migration in `src/main/resources/db/migration/` — never manual DDL, never edit applied migrations.
- **Docs**: `README.md` (zh) and `README.en.md` must stay in sync when behavior changes; also update `CHANGELOG.md`.
- **MVP exclusions**: keep changes minimal and focused; one concern per PR. Lombok is pinned to 1.18.42+ for JDK 25 and declared in `annotationProcessorPaths`.
- **Tests**: backend tests are plain JUnit 5 (no Spring context) for `SkillNormalizer` and the solver; keep them fast and self-contained.
