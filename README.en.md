# AI Resource Orchestrator

> AI-powered project capability planning & workforce orchestration platform
>
> AI 驱动的项目能力规划与人力资源编排平台

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)]()
[![Vue](https://img.shields.io/badge/Vue-3-42b883.svg)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)]()
[![Status](https://img.shields.io/badge/Status-Early%20Development-orange.svg)]()

[简体中文](README.md) | **English**

---

## Table of Contents

- [Introduction](#introduction)
- [Core Capabilities](#core-capabilities)
- [Employee Skill Profile](#employee-skill-profile)
- [AI Skill Recognition](#ai-skill-recognition)
- [Project Management & AI Planning](#project-management--ai-planning)
- [Resource Orchestration](#resource-orchestration)
- [AI Review & Capability Gap Analysis](#ai-review--capability-gap-analysis)
- [Architecture](#architecture)
- [Data Model](#data-model)
- [Module Design](#module-design)
- [Frontend Design](#frontend-design)
- [API Design](#api-design)
- [Quick Start](#quick-start)
- [Product Principles](#product-principles)
- [Scope](#scope)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Introduction

AI Resource Orchestrator is a lightweight, intelligent resource orchestration system for enterprise project teams.

The system uses AI to understand project goals, automatically decompose the project into tasks, identify required skills and roles, and generate resource allocation plans based on employee skill profiles, availability, current workload, and project experience.

The core goal is **not** to build yet another HR, OA, or traditional project management system. It focuses on one question:

> **Tell the AI what project you want to deliver, and the system answers: what capabilities are required, who in the company is the best fit, when they should be involved, how much of their capacity is needed, and what resources are still missing.**

Traditional project management systems typically require project managers to manually handle:

- Task decomposition
- Skill assessment of team members
- People search
- Effort estimation
- Resource coordination
- Conflict resolution
- Cross-project resource balancing

AI Resource Orchestrator turns this process into:

```text
Project Goal
   ↓
AI Project Understanding
   ↓
Automated WBS Generation
   ↓
Task Skill Requirement Identification
   ↓
Employee Capability Matching
   ↓
Resource Optimization Solving
   ↓
Project Staffing Plan
   ↓
Human Confirmation
   ↓
Execution
```

Division of labor:

- **LLM** for project understanding, task decomposition, capability identification, and plan explanation
- **Rule engine / data queries** for candidate filtering
- **Timefold Solver** for real resource optimization under constraints

We deliberately avoid letting an LLM "decide who does what" on its own.

---

## Core Capabilities

### Employee Management

Maintains basic personnel information: name, employee number, department, position, work location, status, working hours, available capacity, and current project load.

Example:

```text
Zhang San

Department: AI R&D
Position: Senior Algorithm Engineer
Location: Shanghai

Current load: 40%
Available capacity: 60%
```

### Skill Taxonomy

A unified enterprise skill library. Skills support categories, hierarchy levels, aliases, descriptions, levels, sources, and confidence.

Example:

```text
Artificial Intelligence
├── Machine Learning
│   ├── XGBoost
│   ├── LightGBM
│   └── CatBoost
│
├── Deep Learning
│   ├── PyTorch
│   ├── TensorFlow
│   └── YOLO
│
├── LLM
│   ├── RAG
│   ├── Agent
│   ├── Prompt Engineering
│   └── Inference & Serving
│
└── MLOps
    ├── Model Serving
    ├── vLLM
    ├── Triton
    └── Kubernetes
```

---

## Employee Skill Profile

Each employee can have multiple skills with a unified level scale:

| Level | Definition |
|---|---|
| L1 | Aware |
| L2 | Can complete tasks under guidance |
| L3 | Can work independently |
| L4 | Proficient, can solve complex problems |
| L5 | Expert, can own architecture and mentor others |

Beyond the level, employee skills also store:

```text
skill level
work experience
last used at
project count
skill source
certified or not
AI confidence
```

Example:

```text
Zhang San

Java              L5
Spring Boot       L5
Python            L4
PyTorch           L4
YOLO              L5
Machine Vision    L4
MES               L3
PLC               L2
Project Mgmt      L3
```

### Skill Sources

Employee skills can come from multiple sources:

```text
SELF        self-assessment
MANAGER     direct manager evaluation
PROJECT     historical projects
RESUME      resume parsing
AI          AI inference
CERTIFICATE certifications
```

The database also stores `level`, `source`, `confidence`, and `verified`, for example:

```text
PyTorch

Level: L4
Source: PROJECT
Confidence: 0.92
Verified: true
```

---

## AI Skill Recognition

AI generates a skill-profile **draft** from experience text; the employee or their manager always confirms it (the Phase 3 core loop is shipped).

Input:

```text
resumes
historical projects
project tasks
project outcomes
technical documents
```

AI output:

```json
{
  "skills": [
    { "name": "Java", "level": 5, "confidence": 0.95 },
    { "name": "Spring Boot", "level": 5, "confidence": 0.94 },
    { "name": "PyTorch", "level": 4, "confidence": 0.86 },
    { "name": "Machine Vision", "level": 4, "confidence": 0.88 }
  ]
}
```

The final profile is always confirmed by the employee or their manager.

**Current implementation (v0.3 dev)**:

- `POST /api/v1/employees/{id}/skills/ai-extract`: submit resume / project-experience text; AI extracts a skill draft (name, L1-L5 level, confidence, rationale) normalized against the skill library by name/alias. `demo` mode uses deterministic keyword rules (nearest tier word, ASCII word boundaries) with no model calls.
- `POST /api/v1/employees/{id}/skills/ai-extract-file`: upload a resume file (txt / md / docx / pdf, up to 5MB); the server extracts its text and feeds the same draft pipeline. In live mode unmatched names carry a deterministic similarity hint (normalized edit distance of at least 0.82 — a suggestion, never an automatic mapping).
- `GET /api/v1/employees/{id}/skills/evidence`: historical task analysis — skill requirements aggregated from the employee's active allocations (project count, total hours, last used), shown as profile evidence.
- `POST /api/v1/employees/{id}/skills/ai-accept`: writes human-checked items into the profile (`source=RESUME/PROJECT/AI`, `verified=false`); existing skills are updated, new ones inserted. AI never persists directly.
- **Automatic skill-update suggestions**: the evidence endpoint also reports profile level vs the level suggested by history; when the profile is missing or lags behind, the UI offers one-click adoption (`source=PROJECT`) — profiles can follow project history while confirmation stays human.

---

## Project Management & AI Planning

The system only provides the project management capabilities needed for resource orchestration. Phase 1 does not attempt to replace ZenTao, Jira, OpenProject, ERP, or OA systems.

Core entities:

```text
Project
  ↓
Milestone
  ↓
Task
```

### Creating Projects with AI

Projects are primarily created through natural language. For example:

```text
Build a vision-based quality inspection system
for automotive components.

12 production lines must be integrated,
interacting with MES and PLC,
covering vision model training, on-site inference
deployment, and quality traceability.

Delivery within 3 months.
```

The AI automatically identifies:

```text
Project type: Industrial Vision AI Project
Duration: 12 weeks
Key capabilities: Computer Vision / YOLO / PyTorch /
Industrial Automation / PLC / MES / Java / MLOps /
On-site Deployment
```

An initial WBS is generated at the same time.

### AI Project Decomposition (AI Planner)

The AI Planner converts project goals into structured tasks:

```text
Automotive Component Vision Inspection Project

├── 1. On-site Research
│   ├── Production line survey
│   ├── PLC interface confirmation
│   └── MES interface confirmation
│
├── 2. Data Collection
│   ├── Camera installation
│   ├── Image acquisition
│   └── Data annotation
│
├── 3. Algorithm Development
│   ├── Data preprocessing
│   ├── YOLO model training
│   ├── Model evaluation
│   └── Model optimization
│
├── 4. Platform Development
│   ├── Backend services
│   ├── MES integration
│   └── Frontend pages
│
├── 5. Inference Deployment
│   ├── Model serving
│   ├── GPU deployment
│   └── Performance testing
│
└── 6. On-site Delivery
    ├── Integration testing
    ├── Acceptance
    └── Training
```

### Task Skill Requirements

Each task can carry skill requirements. For the task "Vision Model Development":

| Skill | Min Level | Weight | Type |
|---|---:|---:|---|
| Computer Vision | L4 | 30% | REQUIRED |
| YOLO | L4 | 30% | REQUIRED |
| PyTorch | L3 | 20% | REQUIRED |
| Machine Vision | L3 | 20% | PREFERRED |

Data structure:

```json
{
  "task": "Vision Model Development",
  "requiredSkills": [
    { "skill": "Computer Vision", "level": 4, "weight": 0.3, "required": true },
    { "skill": "YOLO", "level": 4, "weight": 0.3, "required": true }
  ]
}
```

### Skill Normalization

Skill names produced by an LLM may vary (`CV` / `Computer Vision` / `Machine Vision` / `Industrial Vision Algorithms` / `Visual Recognition`). The system normalizes them:

```text
AI Skill Extraction
   ↓
Exact Name Match
   ↓
Skill Alias Match
   ↓
Semantic Similarity
   ↓
AI-assisted Judgment
   ↓
Unified Skill ID
```

For example, `Computer Vision`, `机器视觉`, and `CV` are all normalized to a single skill entry.

---

## Resource Orchestration

### Candidate Matching

Before optimization, the system first produces candidate employees. For the task "Industrial Vision Model Development":

```text
Zhang San
Skill match     95%
Current load    30%
Available       70%

Li Si
Skill match     88%
Current load    50%
Available       50%

Wang Wu
Skill match     75%
Current load    10%
Available       90%
```

The matching stage handles: skill filtering, skill scoring, department filtering, location filtering, experience filtering, and availability filtering.

### Resource Solver (Timefold Solver)

The actual resource allocation is solved by Timefold Solver — the system never lets the LLM decide the resource plan directly:

```text
AI Planner
    ↓
Task Requirement
    ↓
Candidate Search
    ↓
Timefold Solver
    ↓
Resource Plan
    ↓
AI Reviewer
```

### Hard Constraints

Hard constraints must be satisfied; otherwise the plan is invalid. Phase 1 plans to support:

- **Employee capacity**: total project allocation per employee <= 100%
- **Time conflicts**: an employee cannot take on tasks exceeding their available capacity at any time
- **Skill requirements**: `EmployeeSkill.level >= TaskSkillRequirement.minLevel`
- **Employee unavailability**: leave / business trips / training / blocked time cannot be assigned
- **Task dependencies**: e.g., model training must happen after data collection
- **Project window**: tasks must be executed within the project's time range

### Soft Constraints

The system tries to maximize:

```text
skill match
resource utilization
historical project experience
project priority
work continuity
team stability
location match
```

And tries to minimize:

```text
resource conflicts
cross-project switching
overtime
capability gaps
inefficient use of high-value people
project delays
```

### Optimization Objective

The simplified objective function:

```text
MAX
skill match + project experience + utilization + priority gain

MIN
resource conflict + capability gap + switching + delay
```

Weights are configurable in Phase 1.

### Dynamic Replanning

When base data changes during execution (leave, task/skill adjustments, deactivated members, project window shifts), the system supports an event-driven replan loop:

```text
Event (leave / change / deactivation)
   ↓
Impact analysis  GET /api/v1/projects/{id}/replan/impact
   ↓
Re-solve         POST /api/v1/projects/{id}/replan (own bookings excluded)
   ↓
Compare old vs new (reuses the comparison view)
   ↓
Human confirm → atomic swap (old plan archived, allocations replaced)
```

Impact analysis reports five concrete conflict types: `UNAVAILABLE` (leave/blocked window overlapping an allocation, same rule as the solver), `EMPLOYEE_INACTIVE`, `TASK_DRIFT`, `SKILL_DRIFT`, and `PROJECT_WINDOW`. While active allocations exist, plain `solve` is rejected in favor of `replan`; confirming the new plan archives the old set within the same transaction, so a project always has exactly one active allocation set.

Event reachability takes a **patrol form**: `GET /api/v1/replan/alerts` scans every project with an active plan through the same conflict detection, and the frontend surfaces conflicting projects on the projects tab and list (refreshed right after saving availability). For a replan draft, `POST /api/v1/resource-plans/{id}/explain-diff` produces a diff explanation from the old-vs-new comparison plus impact analysis (deterministic in demo mode) — the AI explains, never decides.

### Execution-side Lifecycle

Three status-transition endpoints close the execution segment after allocations take effect (humans stay the operator):

- `POST /api/v1/tasks/{id}/status`: task `TODO → IN_PROGRESS → DONE / CANCELLED` (`DONE` / `CANCELLED` terminal; a TODO may complete directly). **Completing or cancelling a task also closes its active bookings** (allocation status becomes `COMPLETED` / `CANCELLED`), releasing capacity at once across solving, timeline, capability forecasts and cross-project warnings; a parent task cannot close while children remain open.
- `POST /api/v1/projects/{id}/status`: project `PLANNING → IN_PROGRESS → ON_HOLD / COMPLETED / CANCELLED` (terminal states are final). Terminal transitions require no active bookings (finish tasks or cancel the plan first); `COMPLETED` additionally requires every task closed. Terminal projects can no longer be solved.
- `POST /api/v1/employees/{id}/status`: employee `ACTIVE ⇄ ON_LEAVE`, `ACTIVE → INACTIVE`, `INACTIVE → ACTIVE`. Deactivation or leave drops the employee from candidacy at once (the solver takes ACTIVE only) and triggers the replan patrol on projects that booked them (`EMPLOYEE_INACTIVE` and other conflicts detected through the patrol) — offboarding finally has a clean path (deletion stays FK-protected while allocations exist; deactivate first).

The task list gains a status column with start / complete / cancel actions; project detail and employee profile gain status pickers. Plan cancellation and project completion push `PLAN_CANCELLED` and `PROJECT_COMPLETED` webhooks.

### Cross-project Load Warnings

Plan details include **cross-project load warnings**: this plan's items plus other projects' active allocations are aggregated per week, and any employee reaching 80% or more in a week is listed (rendered as a warning block, and fed to the AI review input as risk context). The hint never blocks confirmation — hard constraints already prevent overbooking; the warning flags high-load risk.

---

## AI Review & Capability Gap Analysis

### AI Review

After solving, the AI does not re-decide resources — it explains them. For example:

```text
Recommend Zhang San as the vision algorithm lead.

Reasons:

1. Computer Vision at L5
2. YOLO at L5
3. Participated in 3 industrial vision projects
4. Average available capacity of 65% during the project
5. Prior collaboration experience with automation
   engineer Li Si

Risk:

Zhang San has another project in weeks 5-6,
with an estimated load of 90%.

Suggestion:

Assign part of the model optimization tasks
to Wang Wu.
```

### Capability Gap Analysis

When current staff cannot satisfy project requirements, the system identifies the gap explicitly. For a demand of "4 people with Machine Vision L4+" and only 2 available:

```text
Resource gap:

Machine Vision Senior Engineer
2 people short
estimated 8 weeks of gap
```

The AI can then generate suggestions: Option A internal transfer / Option B temporary outsourcing / Option C hiring / Option D internal training / Option E adjust the project schedule.

Phase 1 only produces suggestions; nothing is executed automatically.

**Current implementation (v0.2 dev)**: a solved plan distinguishes two kinds of gaps—

- **Skill gap**: no active employee in the company reaches the required level of a REQUIRED skill. Plan details include an aggregated "capability gap summary" (skill, required level, affected tasks, total hours, window, workdays) to support transfer / hiring / training decisions.
- **Capacity/time conflict**: qualified people exist but lack time or capacity, so the task stays unassigned.

---

## Enterprise Integrations & Notifications

First Phase 5 slice: **outbound webhook notifications** (no MQ, no new infrastructure). When a plan is confirmed, or a member's new unavailability overlaps active allocations, the system pushes a message to a group bot **after the transaction commits**; delivery failures are only logged and never break the flow.

```text
NOTIFY_MODE=off|live          # off by default
NOTIFY_PROVIDER=generic|feishu|dingtalk|wecom   # payload format
NOTIFY_WEBHOOK_URL=https://... # group-bot webhook (query tokens masked in logs)
```

- Events: `PLAN_CONFIRMED`, `AVAILABILITY_CONFLICT` (same rule as the replan patrol), `REPLAN_SUGGESTED` (event-driven replan notice), `PLAN_CANCELLED` (plan cancelled, bookings released), `PROJECT_COMPLETED` (project completed)
- Auto replanning (Phase 4 remainder): `REPLAN_AUTO_TRIGGER=off|detect|auto`. Leave windows, skill-profile writes, skill-library edits and project-window changes enqueue an after-commit asynchronous patrol of the affected active plans: `detect` pushes a notice; `auto` additionally drafts a replacement plan (human confirmation still required). A cooldown (`REPLAN_COOLDOWN_MINUTES`, default 30) suppresses repeats; every decision lands in `replan_trigger_log` (`GET /api/v1/replan/triggers`, shown on the settings page)
- Every attempt is recorded in `notification_log` (type/status/duration/masked target), visible on the settings page
- Contract test: `scripts/notify_fixture.py` (local webhook stub) + `scripts/check_notify_contract.py`, same pattern as the LLM contract test

## External Project Sync (Jira / ZenTao / GitLab)

Phase 5 remainder: **one-way import**. Remote projects and issues land as a PLANNING ARO project; tasks get a default sequential workday schedule with status / priority / hours normalized into ARO terms (Jira statusCategory → TODO/IN_PROGRESS/DONE, estimates in seconds → hours). **Skill requirements are not imported** — the integration moves facts only; goals and constraints stay human-authored. `integration_link` maps internal and external objects; incremental refresh (added / updated / cancelled) is allowed while the project has no active allocation set and refused otherwise.

```text
SYNC_JIRA_ENABLED=false|true     SYNC_JIRA_BASE_URL=…  SYNC_JIRA_USERNAME=…  SYNC_JIRA_API_TOKEN=…
SYNC_GITLAB_ENABLED=false|true   SYNC_GITLAB_BASE_URL=…  SYNC_GITLAB_TOKEN=…
SYNC_ZENTAO_ENABLED=false|true   SYNC_ZENTAO_BASE_URL=…  SYNC_ZENTAO_TOKEN=…
# Override when your ZenTao version differs (defaults /api.php/v1/projects, /api.php/v1/projects/{id}/tasks)
SYNC_ZENTAO_PATH_PROJECTS=…      SYNC_ZENTAO_PATH_TASKS=…
```

- API: `GET /api/v1/sync/{source}/projects`, `POST /api/v1/sync/{source}/import`, `POST /api/v1/sync/{source}/refresh`, `GET /api/v1/sync/{source}/links` (source = jira / zentao / gitlab)
- Contract test: `scripts/sync_fixture.py` (local Jira/GitLab/ZenTao stubs) + `scripts/check_sync_contract.py`, same pattern as the notify contract test
- Assumptions: Jira Server/DC REST v2 (Cloud tolerated); ZenTao 18+ OpenAPI v1 (paths overridable); first 100/200 items per source (MVP boundary)

## Optional: Semantic Skill Search (pgvector)

Phase 3 remainder. **Off by default** with zero impact on CRUD and solving; enabling requires a pgvector-enabled PostgreSQL (image `pgvector/pgvector:pg16`) — Flyway then adds `db/migration-semantic` (V900+, no out-of-order needed when enabled later) and creates `skill_embedding`.

```text
SKILL_SEMANTIC_ENABLED=false|true   # master switch
SKILL_SEMANTIC_MODE=local|live      # local: deterministic in-process char-gram hash (default); live: OpenAI-compatible /v1/embeddings
SKILL_SEMANTIC_DIM=256              # local-mode vector dimension
EMBEDDING_MODEL=text-embedding-3-small
```

- API: `GET /api/v1/skills/semantic?q=` (search with cosine scores), `POST /api/v1/skills/semantic/rebuild`; the library is backfilled automatically on first enable
- Unmatched names in AI skill extraction gain vector suggestions (0.5 bar) layered over the deterministic 0.82 similarity hint — still suggestions only, adoption is human
- The local hash is not language understanding: related names score high (Spring Boot ↔ Spring Cloud); cross-lingual synonyms (k8s ↔ Kubernetes) still rely on aliases or the live embedding mode

## Organizational Capability Decisions

First Phase 6 slice (read-only, entirely over internal data): what capabilities are missing, who carries them, and who is irreplaceable.

```text
GET  /api/v1/capability/supply-demand?weeks=12   # skill supply-demand gap forecast (8/12/26 weeks)
GET  /api/v1/capability/key-people               # key capability nodes (bottleneck-skill holders)
GET  /api/v1/capability/leave-impact?employeeId= # what-if impact if a person leaves
GET  /api/v1/capability/scenario?weeks=26        # pipeline scenario (queued projects all start)
GET  /api/v1/capability/trends                   # gap trend across 8/12/26-week windows
POST /api/v1/capability/advise?weeks=12          # AI advice over the gaps (explains, never decides)
GET  /api/v1/capability/supply-weekly?weeks=12   # weekly supply breakdown: demand/supply/gap per week
POST /api/v1/capability/market/import            # import external market benchmarks
```

**Supply/demand method (conservative)**:

- Demand = skill-required hours of unfinished tasks in the window (allocated ones included — supply is discounted accordingly); the bar is the **highest** min_level demanded per skill
- Supply = qualified ACTIVE employees' available hours: workdays × 8h × default capacity% − active allocations − unavailability windows
- Gap people use an **0.8 effective-utilization factor**; advice distinguishes "nobody qualifies (hire/outsource)" from "people exist but lack capacity (reassign/train/hire)"

**Key capability nodes**: holders of bottleneck skills (at most 2 qualified people org-wide), with project counts and allocated hours — which projects compete for the same core people.

**Leave impact (what-if)**: lists the person's active tasks in a 12-week window and checks other qualified people per skill, marking each task replaceable or not.

**Pipeline scenario**: what changes when every queued (PLANNING) project — or an explicit `projectIds` set — runs in parallel; the window stretches to cover their horizons and each skill shows "base gap → scenario gap → delta".

**Gap trends**: gap people compared across 8/12/26-week windows — separating short spikes from structural shortages.

**AI advice**: bullet-point hiring / training / outsourcing / reassignment advice over the forecast (deterministic in demo mode; audited as `CAPABILITY_ADVISE` in live mode) — explanations, never decisions.

**Weekly-refined supply model (Phase 6 remainder)**: `supply-demand?model=weekly` and `GET /capability/supply-weekly` spread demand over each task's workdays, deduct bookings and leave per week, and cap supply by weekly demand before summing — **surplus weeks cannot cover shortage weeks**, so peak shortages the flat model misses become visible (the frontend compares both models).

**External market benchmarks (Phase 6 remainder)**: admins import per-skill market tightness (0–100), salary bands and hiring lead time from external sources (hiring platforms, salary reports) into `market_skill` (`POST /capability/market/import`); rows enrich the forecast and AI advice read-only (e.g. "tightness ≥70 — start hiring early") and never feed the solver.

The frontend "Capability" tab renders all three views; analytics never mutate data — decisions stay human.

## Architecture

Phase 1 sticks to a **modular monolith** — no microservices.

```text
                    Browser
                       │
                 REST / SSE
                       │
        ┌──────────────────────────┐
        │                          │
        │      Spring Boot         │
        │                          │
        │ Employee                 │
        │ Skill                    │
        │ Project                  │
        │ AI Planner               │
        │ Candidate Engine         │
        │ Resource Solver          │
        │ AI Reviewer              │
        │                          │
        └─────────────┬────────────┘
                      │
                      ▼
                PostgreSQL
                      │
                LLM API
                      │
        OpenAI Compatible Provider
```

### Tech Stack

| Layer | Technologies |
|---|---|
| Backend | Java 25, Spring Boot, Spring Security, Spring AI, MyBatis-Plus, Timefold Solver, Flyway, Caffeine |
| Frontend | Vue 3, TypeScript, Vben Admin, Ant Design Vue, Vite |
| Database | PostgreSQL (pgvector optional) |
| AI | Any OpenAI-compatible API (OpenAI / DeepSeek / Qwen / GLM / vLLM / Ollama / others) |

### Explicitly NOT Introduced in Phase 1

To keep the system lightweight and fast to start, the MVP does not use:

```text
Spring Cloud / Nacos / Gateway
Kafka / RocketMQ
Redis
Elasticsearch
Neo4j / Milvus
LangChain / LangGraph
Dify
Kubernetes
```

These can be introduced per module if real needs emerge later.

### Why No Agent Framework (Yet)

The Phase 1 business flow is deterministic:

```text
ProjectPlanner
      ↓
SkillAnalyzer
      ↓
CandidateMatcher
      ↓
ResourceSolver
      ↓
PlanReviewer
```

So it is orchestrated directly with Java services:

```java
ProjectPlan projectPlan =
        projectPlanner.generate(input);

skillService.normalize(projectPlan);

List<EmployeeCandidate> candidates =
        candidateService.search(projectPlan);

ResourcePlan resourcePlan =
        resourceSolver.solve(projectPlan, candidates);

PlanReview review =
        planReviewer.review(resourcePlan);
```

When the system eventually needs long-running execution, autonomous decisions, multi-system calls, failure recovery, complex state machines, human intervention, or multi-round replanning, we will consider an Agent runtime such as LangGraph.

---

## Data Model

Core tables in Phase 1:

```text
sys_user
department
employee

skill_category
skill
skill_alias
employee_skill

project
project_milestone

task
task_dependency
task_skill_requirement

employee_availability

resource_plan
resource_plan_item
resource_allocation

ai_execution
```

Key table fields (full DDL in `src/main/resources/db/migration/`, managed by Flyway):

**Employee**

```text
id / employee_no / name
department_id / position / location / status
weekly_hours / default_capacity
created_at / updated_at
```

**Skill**

```text
id / category_id / name / description
parent_id / status
created_at / updated_at
```

**EmployeeSkill**

```text
id / employee_id / skill_id
level / experience_months
source / confidence / verified
last_used_at
created_at / updated_at
```

**Project**

```text
id / name / description
priority / status
start_date / end_date
manager_id / location
created_at / updated_at
```

**Task**

```text
id / project_id / parent_id
name / description / priority
estimated_hours
start_date / end_date / status
created_at / updated_at
```

**TaskSkillRequirement**

```text
id / task_id / skill_id
min_level / weight
requirement_type (REQUIRED / PREFERRED / OPTIONAL)
created_at / updated_at
```

**EmployeeAvailability**

```text
id / employee_id
start_date / end_date
capacity / type / remark
```

`type` examples: `AVAILABLE` / `LEAVE` / `TRAINING` / `BUSINESS_TRIP` / `BLOCKED`

**ResourceAllocation**

```text
id / project_id / task_id / employee_id
start_date / end_date
allocation / status
created_at / updated_at
```

Example: Zhang San, 2026-10-01 ~ 2026-11-15, Allocation = 60%

**ResourcePlan**

```text
id / project_id
version / score / status
solver_duration
created_by / created_at
```

Multiple plans can be stored (e.g., Plan A lowest risk / Plan B lowest cost / Plan C fastest delivery).

**AIExecution**

```text
id / type / business_id
model / prompt_version
input / output
status / duration / token_usage
created_at
```

Used for troubleshooting, prompt version control, cost statistics, and AI result tracing.

---

## Module Design

### Backend Modules

```text
com.company.orchestrator

├── auth
├── employee
├── department
├── skill
├── project
├── task
├── allocation
├── solver
├── ai
├── system
└── common
```

### AI Module

```text
ai

├── planner
│   └── ProjectPlanner
├── skill
│   ├── SkillExtractor
│   └── SkillNormalizer
├── reviewer
│   └── PlanReviewer
├── client
│   └── LlmClient
└── model
```

The AI module never touches the database directly:

- AI is responsible for: understanding, generating, inferring, explaining
- Business modules are responsible for: validation, persistence, permissions, transactions, constraints

### Solver Module

```text
solver

├── domain
│   ├── ResourceSolution
│   └── ResourceAssignment
├── constraint
│   └── ResourceConstraintProvider
├── service
│   └── ResourceSolverService
└── score
```

The Solver is fully decoupled from AI.

---

## Frontend Design

Planned MVP pages:

```text
Home
Employees
Skill Library
Employee Skill Profile
Projects
AI Project Planning
Resource Orchestration
Settings
```

### Home

The home page is not a traditional executive dashboard. Its primary entry is:

```text
What project are you starting today?
```

```text
┌────────────────────────────────────┐
│                                    │
│  What project are you starting     │
│  today?                            │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ Build an industrial vision …   │ │
│ └────────────────────────────────┘ │
│                                    │
│                    [ Plan with AI ] │
│                                    │
└────────────────────────────────────┘
```

### AI Project Planning Page

Displays project summary, duration, milestones, WBS, tasks, skill requirements, expected roles, headcount, and effort.

Users can accept, edit, delete, or regenerate — **every AI output remains editable by humans**.

### Resource Orchestration Page

- **Left**: Projects → Tasks
- **Center**: recommended people, skill match, current load, availability
- **Right**: resource plan, risks, capability gaps, AI suggestions

The "Timeline" tab provides a weekly team capacity timeline / heatmap (27-week horizon, green/amber/red load tiers, hover for booking details); the project detail page renders a day-granularity task Gantt (weekend shading, priority-coloured bars, milestone diamonds).

### Roles & Permissions

Only four roles in the MVP:

```text
ADMIN
PROJECT_MANAGER
DEPARTMENT_MANAGER
EMPLOYEE
```

---

## API Design

All APIs use the `/api/v1` prefix, for example:

```text
GET  /api/v1/employees
POST /api/v1/employees
GET  /api/v1/skills
GET  /api/v1/projects
POST /api/v1/projects
POST /api/v1/projects/{id}/ai-plan
POST /api/v1/projects/{id}/solve
GET  /api/v1/resource-plans/{id}
```

### AI API

```text
POST /api/v1/ai/project-plan
```

Request:

```json
{
  "description": "Build an industrial vision quality inspection system, online in three months..."
}
```

Response:

```json
{
  "project": {},
  "milestones": [],
  "tasks": [],
  "skills": [],
  "roles": []
}
```

### Replanning API

```text
GET  /api/v1/projects/{id}/replan/impact      # change impact analysis (five conflict types)
POST /api/v1/projects/{id}/replan             # re-solve against current data (new draft)
GET  /api/v1/replan/alerts                    # patrol: conflicting projects across the org
POST /api/v1/resource-plans/{id}/explain-diff # old-vs-new diff explanation for a replan draft
```

### Lifecycle API

```text
POST /api/v1/tasks/{id}/status       # task transition (closing a task closes its bookings)
POST /api/v1/projects/{id}/status    # project lifecycle (terminal states need no active bookings)
POST /api/v1/employees/{id}/status   # employee offboarding / leave / return (triggers the patrol)
```

### Skill Recognition API

```text
POST /api/v1/employees/{id}/skills/ai-extract   # experience text -> skill draft (nothing persisted)
POST /api/v1/employees/{id}/skills/ai-accept    # human-confirmed write into the profile
GET  /api/v1/employees/{id}/skills/evidence     # skill evidence from historical allocations
```

### Solver API

```text
POST /api/v1/projects/{projectId}/solve
```

Parameters:

```json
{
  "strategy": "BALANCED"
}
```

Implemented strategies: `BALANCED` (default) / `BEST_SKILL_MATCH` (skill-match weight ×4) / `LOWEST_RISK` (soft penalty when one person's total allocation exceeds 60%, encouraging distribution). `FASTEST` / `LOWEST_COST` remain future extensions.

### AI Streaming

AI project plan generation uses SSE (avoiding WebSocket in Phase 1):

```text
POST /ai/project-plan

↓ SSE

Analyzing project goals...
Decomposing tasks...
Identifying required skills...
Estimating resource needs...
Done.
```

---

## Quick Start

### Prerequisites

```text
JDK 25
Node.js 22+
PostgreSQL 16+
Maven 3.9+ (or just use the bundled Maven Wrapper)
```

### Option 1: Docker Compose (recommended)

```bash
git clone https://github.com/hx346/ai-resource-orchestrator.git
cd ai-resource-orchestrator

# Set an administrator password before first startup (at least 12 characters)
cp .env.example .env
ADMIN_PASSWORD='change-this-to-a-long-secret'
AI_MODE=demo

docker compose up -d
```

Open `http://localhost:8080` and sign in as `admin`. The image builds and serves the Vue frontend as well as the API.

### Option 2: Local Development

```bash
# 1. Create the database
psql -U postgres -c "CREATE DATABASE ai_resource_orchestrator;"

# 2. Start the backend (Flyway initializes the schema automatically)
./mvnw spring-boot:run
# API docs available at http://localhost:8080/swagger-ui.html after startup

# 3. Start the frontend
cd web
pnpm install
pnpm dev
```

### Repository Layout

```text
ai-resource-orchestrator
├── src/main/java/com/company/orchestrator   # Backend modular monolith (auth/employee/skill/project/solver/ai/...)
├── src/main/resources/application.yml        # Main configuration
├── src/main/resources/db/migration           # Flyway migrations (PostgreSQL dialect)
├── web/                                      # Frontend (Vue 3 + TypeScript + Vben Admin)
├── Dockerfile                                # Backend image build
├── docker-compose.yml                        # One-command startup: app + postgres
├── .env.example                              # Environment variable template
└── README.md / README.en.md                  # Bilingual docs
```

### Configuration

Key configuration is injected via environment variables (template in [.env.example](.env.example)):

| Variable | Description | Default |
|---|---|---|
| `AI_BASE_URL` | OpenAI-compatible endpoint | `https://api.openai.com` |
| `AI_MODE` | `off` / `demo` / `live` | `off` |
| `AI_CHAT_PROVIDER` | Spring AI provider, use `openai` in live mode | `none` |
| `AI_API_KEY` | LLM API key, required in live mode | empty |
| `AI_MODEL` | Model name | `gpt-4o-mini` |
| `NOTIFY_MODE` | Outbound notifications `off` / `live` | `off` |
| `NOTIFY_PROVIDER` | Payload format `generic` / `feishu` / `dingtalk` / `wecom` | `generic` |
| `NOTIFY_WEBHOOK_URL` | Group-bot webhook URL, required in live mode | empty |
| `REPLAN_AUTO_TRIGGER` | Event-driven auto replanning `off` / `detect` / `auto` | `off` |
| `REPLAN_COOLDOWN_MINUTES` | Trigger cooldown window (minutes) | `30` |
| `SKILL_SEMANTIC_ENABLED` | pgvector semantic search switch | `false` |
| `SKILL_SEMANTIC_MODE` | Embedding mode `local` (deterministic hash) / `live` (/v1/embeddings) | `local` |
| `SYNC_JIRA_ENABLED` etc. | Jira / GitLab / ZenTao sync switches and credentials (see "External Project Sync") | off |
| `ADMIN_PASSWORD` | First-start admin password (at least 12 characters) | empty (required) |
| `POSTGRES_HOST` / `POSTGRES_PORT` | Database host / port | `localhost` / `5432` |
| `POSTGRES_DB` | Database name | `ai_resource_orchestrator` |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Database credentials | `postgres` / `postgres` |

Database connection:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ai_resource_orchestrator
    username: postgres
    password: postgres
```

Flyway initializes the database automatically — no manual DDL needed.

### FAQ

- **Do I need Redis?** No. Phase 1 caching uses in-process Caffeine.
- **Do I need OpenAI?** No. Any OpenAI Chat Completion-compatible service works (DeepSeek, Qwen, GLM, vLLM, Ollama, etc.).
- **Do I need pgvector?** No. Semantic search is off by default with zero dependencies; when needed, run the `pgvector/pgvector:pg16` image with `SKILL_SEMANTIC_ENABLED=true` (see "Optional: Semantic Skill Search").
- **Do I need Maven installed?** No. The repo bundles the Maven Wrapper (`./mvnw`), which downloads a pinned Maven version automatically.

---

## Product Principles

1. **AI never makes final constraint decisions**: AI understands, plans, extracts, suggests, and explains; the Solver optimizes, enforces constraints, resolves conflicts, and produces the final solution.
2. **All AI output is editable**: there is no "generated and locked". Tasks, skills, roles, and resource plans can all be modified by humans.
3. **Human in the loop**: important allocations must be confirmed by a person: AI suggestion → Solver plan → Project Manager confirmation → official allocation.
4. **Lightweight first**: no new infrastructure before a real need exists.
5. **Monolith first**: no microservice split before real scaling pressure exists.

---

## Scope

### Explicitly Out of MVP Scope

```text
payroll / compensation / performance
recruiting ATS / attendance / OA
procurement / finance / expense reports
contract management / CRM
```

This system is not an ERP.

### MVP Scope

| Module | Contents |
|---|---|
| Employee | Employee CRUD, departments, capacity |
| Skill | Skill CRUD, skill categories, skill aliases, employee skills |
| Project | Project CRUD, tasks, task dependencies, task skill requirements |
| AI | Natural-language project understanding, WBS generation, skill requirement generation |
| Matching | Candidate search, skill match scoring |
| Solver | Automated staffing under capacity / skill / time constraints and task dependencies |
| Review | AI plan explanation, risk hints, capability gap analysis |

### MVP Success Criteria

The first version must complete the full loop:

```text
Enter 10 employees
↓
Enter employee skills
↓
Create a project via natural language
↓
AI generates tasks automatically
↓
AI generates skill requirements automatically
↓
System filters candidate employees
↓
Solver generates staffing automatically
↓
AI explains the plan
↓
Project manager confirms
```

Once this loop runs end to end, the MVP is a success.

---

## Roadmap

- **Phase 1 — Foundation MVP**: Employee, Skill, Project, AI Planner, Skill Matching, Timefold Solver, Resource Plan
- **Phase 2 — Richer resource management**: multi-project orchestration, resource timeline, capacity heatmap, cross-project conflicts, plan comparison, capability gap analysis (in progress: skill-level gap analysis and the weekly capacity timeline are shipped)
- **Phase 3 — Automated skill profiles**: resume parsing, project history parsing, historical task analysis, AI skill profile, automatic skill updates (shipped: text/file draft extraction + normalization with similarity hints + history-evidence adoption + pgvector vector semantic search, optional)
- **Phase 4 — Dynamic replanning**: automatically re-solve on delays / leave / requirement changes / priority changes / new hires (Event → Impact Analysis → Solver → New Plan → AI explanation → Human confirmation) (shipped: impact analysis + replan solving + atomic swap + org-wide patrol alerts + diff explanations + event-driven triggers (detect/auto, drafts still need human confirmation) + REPLAN_SUGGESTED push)
- **Phase 5 — Enterprise integrations**: Jira, ZenTao, GitLab, GitHub, Feishu, DingTalk, WeCom, HR systems, ERP, MES (shipped: Feishu / DingTalk / WeCom / generic outbound webhook notifications + one-way Jira / ZenTao / GitLab project import with incremental refresh; GitHub / HR / ERP / MES come later)
- **Phase 6 — Organizational capability decisions**: skill supply/demand gap forecasting based on the future project pipeline, with hiring / training / outsourcing / transfer suggestions — evolving into an enterprise resource intelligence platform (shipped: gap forecast (flat and weekly-refined models) + key people + leave impact + pipeline scenarios + gap trends + AI advice + imported external market benchmarks; direct market-data connectors are future work)

### Long-term Direction

Beyond "who fits this task?", the system aims to answer:

> Does this company have the capability to deliver this project?
>
> If these projects all start within six months, what capabilities must be added?
>
> Who are the key capability nodes in the organization, and which projects compete for them?
>
> Should we hire, train, or outsource? If a key person leaves, which projects are affected?

```text
Project Demand
      +
Enterprise Capability
      +
Resource Availability
      +
AI Planning
      +
Optimization
=
Enterprise Resource Intelligence
```

### Positioning

AI Resource Orchestrator does not want to be another Jira / ZenTao / HR system / OA / ERP. It wants to be the layer above them:

> **An AI project capability planning & resource orchestration layer**
>
> Tell the system what to do — it tells you what capabilities are needed, who fits best, how to schedule them, and what is still missing.

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) and follow our [Code of Conduct](CODE_OF_CONDUCT.md).

- Open an issue: [Issues](https://github.com/hx346/ai-resource-orchestrator/issues)
- Security vulnerabilities: report privately as described in [SECURITY.md](SECURITY.md) — do not open public issues

The project is in early development (🚧 Early Development / MVP), currently focused on domain modeling, the employee capability model, the AI Project Planner, the Resource Solver, and closing the MVP loop.

---

## License

This project is open-sourced under the [MIT License](LICENSE).

---

**AI Resource Orchestrator**

> From project intent to workforce allocation.
