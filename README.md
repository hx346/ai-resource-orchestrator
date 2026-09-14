# AI Resource Orchestrator

> AI 驱动的项目能力规划与人力资源编排平台
>
> AI-powered project capability planning & workforce orchestration platform

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)]()
[![Vue](https://img.shields.io/badge/Vue-3-42b883.svg)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)]()
[![Status](https://img.shields.io/badge/Status-Early%20Development-orange.svg)]()

**简体中文** | [English](README.en.md)

---

## 目录

- [项目简介](#项目简介)
- [核心能力](#核心能力)
- [员工能力画像](#员工能力画像)
- [AI 技能识别](#ai-技能识别)
- [项目管理与 AI 规划](#项目管理与-ai-规划)
- [资源编排](#资源编排)
- [AI Review 与能力缺口分析](#ai-review-与能力缺口分析)
- [系统架构](#系统架构)
- [数据模型](#数据模型)
- [模块设计](#模块设计)
- [前端设计](#前端设计)
- [API 设计](#api-设计)
- [快速开始](#快速开始)
- [产品原则](#产品原则)
- [范围边界](#范围边界)
- [Roadmap](#roadmap)
- [参与贡献](#参与贡献)
- [License](#license)

---

## 项目简介

AI Resource Orchestrator 是一个面向企业项目团队的轻量级智能资源编排系统。

系统通过 AI 理解项目目标，自动拆解项目任务、识别所需技能和角色，并结合员工技能画像、可用时间、当前负载、项目经验等信息，自动生成资源分配方案。

项目的核心目标不是构建新的 HR、OA 或传统项目管理系统，而是解决一个更聚焦的问题：

> **告诉 AI 要做什么项目，由系统回答需要什么能力、公司里谁最合适、什么时候投入、投入多少，以及当前还缺什么资源。**

传统项目管理系统通常要求项目经理人工完成：

- 项目任务拆解
- 人员技能判断
- 人员搜索
- 工作量评估
- 项目资源协调
- 人员冲突处理
- 多项目资源平衡

AI Resource Orchestrator 希望将上述流程转变为：

```text
项目目标
   ↓
AI 项目理解
   ↓
自动生成 WBS
   ↓
识别任务能力需求
   ↓
匹配企业员工能力
   ↓
资源优化求解
   ↓
生成项目人员方案
   ↓
人工确认
   ↓
项目执行
```

技术分工上：

- **LLM** 用于项目理解、任务拆解、能力识别和方案解释
- **规则引擎 / 数据查询** 用于候选人员筛选
- **Timefold Solver** 用于真正的资源优化与约束求解

避免直接让大模型"拍脑袋决定谁做什么"。

---

## 核心能力

### 员工管理

维护企业人员基础信息，包括姓名、工号、所属部门、岗位、工作地点、当前状态、工作时间、可用 Capacity、当前项目负载。

示例：

```text
张三

部门：AI研发部
岗位：高级算法工程师
地点：上海

当前负载：40%
可用资源：60%
```

### 技能体系

建立企业统一技能库。技能支持分类、层级、别名、描述、技能等级、技能来源、技能置信度。

示例：

```text
人工智能
├── 机器学习
│   ├── XGBoost
│   ├── LightGBM
│   └── CatBoost
│
├── 深度学习
│   ├── PyTorch
│   ├── TensorFlow
│   └── YOLO
│
├── 大模型
│   ├── RAG
│   ├── Agent
│   ├── Prompt Engineering
│   └── 推理部署
│
└── MLOps
    ├── Model Serving
    ├── vLLM
    ├── Triton
    └── Kubernetes
```

---

## 员工能力画像

每个员工可以拥有多个技能，技能采用统一等级：

| 等级 | 定义 |
|---|---|
| L1 | 了解 |
| L2 | 可以在指导下完成任务 |
| L3 | 可以独立完成工作 |
| L4 | 熟练，能够解决复杂问题 |
| L5 | 专家，可以负责架构、指导其他成员 |

员工技能不仅保存 Level，还可以保存：

```text
技能等级
工作经验
最近使用时间
项目数量
技能来源
是否认证
AI置信度
```

示例：

```text
张三

Java              L5
Spring Boot       L5
Python            L4
PyTorch           L4
YOLO              L5
工业视觉          L4
MES               L3
PLC               L2
项目管理          L3
```

### 技能来源

员工技能可以来自多个来源：

```text
SELF        员工自评
MANAGER     直属领导评价
PROJECT     历史项目
RESUME      简历识别
AI          AI 自动推断
CERTIFICATE 认证 / 证书
```

数据库同时保存 `level`、`source`、`confidence`、`verified`，例如：

```text
PyTorch

Level: L4
Source: PROJECT
Confidence: 0.92
Verified: true
```

---

## AI 技能识别

后续支持通过 AI 自动生成员工初始能力画像。

输入：

```text
简历
历史项目
项目任务
项目成果
技术文档
```

AI 输出：

```json
{
  "skills": [
    { "name": "Java", "level": 5, "confidence": 0.95 },
    { "name": "Spring Boot", "level": 5, "confidence": 0.94 },
    { "name": "PyTorch", "level": 4, "confidence": 0.86 },
    { "name": "工业视觉", "level": 4, "confidence": 0.88 }
  ]
}
```

最终由员工或负责人确认。

---

## 项目管理与 AI 规划

系统只提供完成资源编排所需要的基础项目管理能力。第一阶段不试图替代禅道、Jira、OpenProject、ERP、OA。

核心实体：

```text
Project
  ↓
Milestone
  ↓
Task
```

### AI 创建项目

本系统优先支持自然语言创建项目。例如输入：

```text
建设一套汽车零部件视觉质量检测系统。

需要接入 12 条生产线，
与 MES 和 PLC 进行数据交互，
完成视觉模型训练、现场推理部署和质量追溯。

要求 3 个月完成。
```

AI 自动识别：

```text
项目类型：工业视觉 AI 项目
周期：12 周
关键能力：计算机视觉 / YOLO / PyTorch / 工业自动化 / PLC / MES / Java / MLOps / 现场部署
```

同时生成初步 WBS。

### AI 项目拆解（AI Planner）

AI Planner 将项目目标转换成结构化任务：

```text
汽车零部件视觉检测项目

├── 1. 现场调研
│   ├── 产线调研
│   ├── PLC接口确认
│   └── MES接口确认
│
├── 2. 数据采集
│   ├── 相机安装
│   ├── 图像采集
│   └── 数据标注
│
├── 3. 算法开发
│   ├── 数据预处理
│   ├── YOLO模型训练
│   ├── 模型评估
│   └── 模型优化
│
├── 4. 平台开发
│   ├── 后端服务
│   ├── MES接口
│   └── 前端页面
│
├── 5. 推理部署
│   ├── 模型服务
│   ├── GPU部署
│   └── 性能测试
│
└── 6. 现场交付
    ├── 联调
    ├── 验收
    └── 培训
```

### 任务能力需求

每个任务可以关联能力需求。例如任务"视觉模型开发"：

| Skill | 最低等级 | 权重 | 类型 |
|---|---:|---:|---|
| 计算机视觉 | L4 | 30% | REQUIRED |
| YOLO | L4 | 30% | REQUIRED |
| PyTorch | L3 | 20% | REQUIRED |
| 工业视觉 | L3 | 20% | PREFERRED |

数据结构：

```json
{
  "task": "视觉模型开发",
  "requiredSkills": [
    { "skill": "计算机视觉", "level": 4, "weight": 0.3, "required": true },
    { "skill": "YOLO", "level": 4, "weight": 0.3, "required": true }
  ]
}
```

### Skill Normalize（技能归一化）

LLM 输出的技能名称可能不统一（如 `CV` / `Computer Vision` / `机器视觉` / `工业视觉算法` / `视觉识别`），系统需要进行技能归一化：

```text
AI提取Skill
   ↓
精确名称匹配
   ↓
Skill Alias匹配
   ↓
语义相似度
   ↓
AI辅助判断
   ↓
统一Skill ID
```

例如 `Computer Vision`、`机器视觉`、`CV` 最终归一化为 `计算机视觉`。

---

## 资源编排

### 候选员工匹配

在进入优化求解之前，系统先生成候选员工。例如任务"工业视觉模型开发"：

```text
张三
技能匹配 95%
当前负载 30%
可用资源 70%

李四
技能匹配 88%
当前负载 50%
可用资源 50%

王五
技能匹配 75%
当前负载 10%
可用资源 90%
```

匹配阶段主要负责：技能过滤、技能评分、部门筛选、地点筛选、经验筛选、可用时间筛选。

### Resource Solver（Timefold Solver）

真正的人员资源分配由 Timefold Solver 完成，系统不让 LLM 直接决定资源方案：

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

### Solver 硬约束

硬约束必须满足，否则方案无效。第一阶段计划支持：

- **人员 Capacity**：员工项目占用总和 <= 100%
- **时间冲突**：员工不能在同一时间承担超过可用资源的任务
- **技能要求**：`EmployeeSkill.level >= TaskSkillRequirement.minLevel`
- **员工不可用时间**：休假 / 出差 / 培训 / 不可安排时间不能分配任务
- **任务依赖**：如模型训练必须在数据采集之后进行
- **项目周期**：任务必须在项目允许时间范围内执行

### Solver 软约束

系统将尽量优化：

```text
技能匹配度
人员利用率
历史项目经验
项目优先级
员工连续工作
团队稳定性
地点匹配
```

同时尽量减少：

```text
人员冲突
跨项目切换
加班
能力缺口
高价值人员低效使用
项目延期
```

### 优化目标

简化后的目标函数可以理解为：

```text
MAX
技能匹配 + 项目经验 + 资源利用率 + 项目优先级收益

MIN
资源冲突 + 能力缺口 + 人员切换 + 项目延期
```

第一阶段具体权重由系统配置。

---

## AI Review 与能力缺口分析

### AI Review

求解完成以后，AI 不重新决定资源，而是负责解释。例如：

```text
推荐张三担任视觉算法负责人。

原因：

1. 计算机视觉能力 L5
2. YOLO 能力 L5
3. 曾参与 3 个工业视觉项目
4. 项目期间平均可用 Capacity 为 65%
5. 与自动化工程师李四已有历史合作经验

风险：

张三在第 5~6 周还有另外一个项目，
预计资源占用达到 90%。

建议：

将模型优化任务部分分配给王五。
```

### 能力缺口分析

如果企业当前人员无法满足项目要求，系统需要明确识别 Gap。例如需求"工业视觉 L4+ 共 4 人"，当前可用 2 人，则输出：

```text
资源缺口：

工业视觉高级工程师
缺少 2 人
预计缺口持续 8 周
```

AI 后续可以生成：方案 A 内部调配 / 方案 B 临时外包 / 方案 C 招聘 / 方案 D 内部培训 / 方案 E 调整项目周期。

第一阶段只输出建议，不直接执行。

**当前实现（v0.2 dev）**：求解方案自动区分两类缺口——

- **技能缺口**：某必备技能全公司无人达到要求等级。方案详情按技能聚合输出「能力缺口汇总」（技能、要求等级、涉及任务数、总工时、时间窗、工作日数），直接支撑调配 / 招聘 / 培训决策。
- **时间容量冲突**：有人具备技能但时间或容量不足，任务暂未分配。

---

## 系统架构

第一阶段坚持**模块化单体架构**，不使用微服务。

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

### 技术栈

| 层 | 技术 |
|---|---|
| Backend | Java 25、Spring Boot、Spring Security、Spring AI、MyBatis-Plus、Timefold Solver、Flyway、Caffeine |
| Frontend | Vue 3、TypeScript、Vben Admin、Ant Design Vue、Vite |
| Database | PostgreSQL（可选 pgvector） |
| AI | 任意 OpenAI Compatible API（OpenAI / DeepSeek / Qwen / GLM / vLLM / Ollama 等） |

### 第一阶段明确不引入

为了保证系统轻量和快速启动，MVP 阶段不使用：

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

如果未来确实出现对应需求，再按模块引入。

### 为什么暂时不使用 Agent Framework

第一阶段业务流程相对确定：

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

因此直接通过 Java Service 编排：

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

未来当系统出现长时间运行、自主决策、多系统调用、失败恢复、复杂状态机、人工介入、多轮重规划等需求时，再考虑引入 LangGraph 等 Agent Runtime。

---

## 数据模型

第一阶段核心表：

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

关键表字段（完整 DDL 见 `src/main/resources/db/migration/`，由 Flyway 管理）：

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

其中 type 例如：`AVAILABLE` / `LEAVE` / `TRAINING` / `BUSINESS_TRIP` / `BLOCKED`

**ResourceAllocation**

```text
id / project_id / task_id / employee_id
start_date / end_date
allocation / status
created_at / updated_at
```

例如：张三 2026-10-01 ~ 2026-11-15，Allocation = 60%

**ResourcePlan**

```text
id / project_id
version / score / status
solver_duration
created_by / created_at
```

允许保存多个方案（如方案A 最低风险 / 方案B 最低成本 / 方案C 最快交付）。

**AIExecution**

```text
id / type / business_id
model / prompt_version
input / output
status / duration / token_usage
created_at
```

用于问题排查、Prompt 版本控制、成本统计、AI 结果追踪。

---

## 模块设计

### 后端模块

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

### AI 模块

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

AI 模块不直接操作数据库：

- AI 负责：理解、生成、推断、解释
- 业务模块负责：验证、持久化、权限、事务、约束

### Solver 模块

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

Solver 与 AI 完全解耦。

---

## 前端设计

MVP 第一阶段页面规划：

```text
首页
员工管理
技能库
员工能力画像
项目管理
AI项目规划
资源编排
系统设置
```

### 首页

首页不是传统管理驾驶舱，主要入口是：

```text
你准备开展什么项目？
```

```text
┌────────────────────────────────────┐
│                                    │
│   今天准备开展什么项目？            │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 建设一个工业视觉检测系统……      │ │
│ └────────────────────────────────┘ │
│                                    │
│                      [ AI规划 ]     │
│                                    │
└────────────────────────────────────┘
```

### AI 项目规划页面

显示项目摘要、项目周期、Milestone、WBS、Task、Skill Requirement、预计角色、预计人数、预计工时。

用户可以接受、修改、删除、重新生成——**所有 AI 结果都必须允许人工调整**。

### 资源编排页面

- **左侧**：项目 → 任务
- **中间**：推荐人员、技能匹配度、当前负载、Availability
- **右侧**：资源方案、风险、能力 Gap、AI 建议

「资源排期」页已提供按周聚合的全员 Capacity Timeline / Heatmap（27 周视野，绿/黄/红三档负荷，悬停查看项目占用明细）。后续增加：Gantt。

### 系统权限

MVP 阶段只考虑四种角色：

```text
ADMIN
PROJECT_MANAGER
DEPARTMENT_MANAGER
EMPLOYEE
```

---

## API 设计

统一使用 `/api/v1` 前缀。例如：

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

请求：

```json
{
  "description": "建设一套工业视觉质检系统，三个月上线……"
}
```

返回：

```json
{
  "project": {},
  "milestones": [],
  "tasks": [],
  "skills": [],
  "roles": []
}
```

### Solver API

```text
POST /api/v1/projects/{projectId}/solve
```

参数：

```json
{
  "strategy": "BALANCED"
}
```

预留策略：`BALANCED` / `FASTEST` / `LOWEST_COST` / `LOWEST_RISK` / `BEST_SKILL_MATCH`，第一阶段真正实现 `BALANCED`，其余作为未来扩展。

### AI Streaming

AI 生成项目计划采用 SSE（避免第一阶段引入 WebSocket）：

```text
POST /ai/project-plan

↓ SSE

正在分析项目目标……
正在拆解任务……
正在识别所需技能……
正在估算资源需求……
完成。
```

---

## 快速开始

### 环境要求

```text
JDK 25
Node.js 22+
PostgreSQL 16+
Maven 3.9+（或直接使用 Maven Wrapper）
```

### 方式一：Docker Compose（推荐）

```bash
git clone https://github.com/hx346/ai-resource-orchestrator.git
cd ai-resource-orchestrator

# 首次启动必须设置管理员密码（至少 12 个字符）
cp .env.example .env
ADMIN_PASSWORD='change-this-to-a-long-secret'
AI_MODE=demo

docker compose up -d
```

浏览器打开 `http://localhost:8080`，使用 `admin` 和 `ADMIN_PASSWORD` 登录。Docker 镜像会同时构建并托管 Vue 前端。

### 方式二：本地开发

```bash
# 1. 创建数据库
psql -U postgres -c "CREATE DATABASE ai_resource_orchestrator;"

# 2. 启动后端（Flyway 自动初始化数据库）
./mvnw spring-boot:run
# 启动后访问 http://localhost:8080/swagger-ui.html 查看接口文档

# 3. 启动前端
cd web
pnpm install
pnpm dev
```

### 目录结构

```text
ai-resource-orchestrator
├── src/main/java/com/company/orchestrator   # 后端模块化单体（auth/employee/skill/project/solver/ai/...）
├── src/main/resources/application.yml        # 主配置
├── src/main/resources/db/migration           # Flyway 数据库迁移脚本（PostgreSQL 方言）
├── web/                                      # 前端（Vue 3 + TypeScript + Vben Admin）
├── Dockerfile                                # 后端镜像构建
├── docker-compose.yml                        # app + postgres 一键启动
├── .env.example                              # 环境变量模板
└── README.md / README.en.md                  # 中英文文档
```

### 配置

关键配置通过环境变量注入（模板见 [.env.example](.env.example)）：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `AI_BASE_URL` | OpenAI 兼容服务地址 | `https://api.openai.com` |
| `AI_MODE` | `off` / `demo` / `live` | `off` |
| `AI_CHAT_PROVIDER` | Spring AI 提供方，`live` 时使用 `openai` | `none` |
| `AI_API_KEY` | LLM API Key，`live` 时必填 | 空 |
| `AI_MODEL` | 模型名称 | `gpt-4o-mini` |
| `ADMIN_PASSWORD` | 首次创建管理员的密码（至少 12 字符） | 空（必填） |
| `POSTGRES_HOST` / `POSTGRES_PORT` | 数据库地址 | `localhost` / `5432` |
| `POSTGRES_DB` | 数据库名 | `ai_resource_orchestrator` |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | 数据库账号 | `postgres` / `postgres` |

数据库连接配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ai_resource_orchestrator
    username: postgres
    password: postgres
```

Flyway 会自动初始化数据库，无需手工建表。

### 常见问题

- **必须用 Redis 吗？** 不需要。第一阶段缓存使用应用内 Caffeine。
- **必须用 OpenAI 吗？** 不需要。任何兼容 OpenAI Chat Completion API 的服务（DeepSeek、Qwen、GLM、vLLM、Ollama 等）均可接入。
- **必须安装 pgvector 吗？** 不需要。pgvector 为可选能力，MVP 阶段可关闭。
- **必须用 Maven 吗？** 不需要。仓库内置 Maven Wrapper（`./mvnw`），会自动下载指定版本的 Maven。

---

## 产品原则

1. **AI 不负责最终约束决策**：AI 负责理解、规划、提取、建议、解释；Solver 负责优化、约束、资源冲突、最终求解。
2. **所有 AI 结果可编辑**：系统不存在"AI 生成后不可修改"，Task / Skill / Role / Resource Plan 都允许人工修改。
3. **Human in the Loop**：重要资源分配必须由人确认：AI 建议 → Solver 方案 → Project Manager 确认 → 正式 Allocation。
4. **轻量优先**：没有明确需求之前不增加新的基础设施。
5. **单体优先**：没有真实扩展压力之前不拆微服务。

---

## 范围边界

### MVP 明确不做什么

```text
工资 / 薪酬 / 绩效
招聘 ATS / 考勤 / OA
采购 / 财务 / 费用报销
合同管理 / CRM
```

本系统不是 ERP。

### MVP 范围

| 模块 | 内容 |
|---|---|
| Employee | 员工 CRUD、部门、Capacity |
| Skill | Skill CRUD、Skill Category、Skill Alias、Employee Skill |
| Project | Project CRUD、Task、Task Dependency、Task Skill Requirement |
| AI | 项目自然语言理解、WBS 生成、Skill Requirement 生成 |
| Matching | 候选人员搜索、技能匹配评分 |
| Solver | 人员资源自动编排、Capacity / Skill / 时间约束、任务依赖 |
| Review | AI 解释资源方案、风险提示、能力缺口分析 |

### MVP 成功标准

第一版本至少完成完整闭环：

```text
录入 10 个员工
↓
录入员工技能
↓
自然语言创建项目
↓
AI 自动生成任务
↓
AI 自动生成技能需求
↓
系统筛选候选员工
↓
Solver 自动生成人员安排
↓
AI 解释方案
↓
项目经理确认
```

只要该链路完整跑通，就认为 MVP 成功。

---

## Roadmap

- **Phase 1 — 基础 MVP**：Employee、Skill、Project、AI Planner、Skill Matching、Timefold Solver、Resource Plan
- **Phase 2 — 增强项目资源管理**：多项目编排、资源 Timeline、Capacity Heatmap、项目资源冲突、多方案对比、能力 Gap 分析（进行中：技能级缺口分析与周度排期热力图已落地）
- **Phase 3 — 自动能力画像**：简历解析、项目经历解析、历史任务分析、AI Skill Profile、技能自动更新
- **Phase 4 — 动态重规划**：项目延期 / 人员请假 / 需求变化 / 优先级变化 / 新人加入时自动触发重新求解（Event → Impact Analysis → Solver → New Plan → AI 解释 → 人工确认）
- **Phase 5 — 企业系统集成**：Jira、禅道、GitLab、GitHub、飞书、钉钉、企业微信、HR 系统、ERP、MES
- **Phase 6 — 组织能力决策**：基于未来项目 Pipeline 做 Skill 供需 Gap 预测，输出招聘 / 培训 / 外包 / 调岗建议，演进为企业能力资源决策平台

### 长期方向

未来系统希望回答的不只是"谁适合这个任务？"，还包括：

> 这家公司有没有能力做这个项目？
>
> 如果未来半年同时启动这些项目，需要增加什么能力？
>
> 哪些员工是当前组织中的关键能力节点？哪些项目正在争夺相同的核心人员？
>
> 应该招聘、培养还是外包？如果某个核心人员离开，哪些项目会受到影响？

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

### 项目定位

AI Resource Orchestrator 不希望成为另一个 Jira / 禅道 / HR 系统 / OA / ERP，而是希望成为这些系统之上的：

> **AI 项目能力规划与资源编排层**
>
> 告诉系统要做什么，系统自动告诉你需要什么能力、谁最合适、如何安排，以及还缺什么。

---

## 参与贡献

欢迎参与项目建设！请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解贡献流程，并遵守 [行为准则](CODE_OF_CONDUCT.md)。

- 提交问题：[Issues](https://github.com/hx346/ai-resource-orchestrator/issues)
- 安全漏洞：请按 [SECURITY.md](SECURITY.md) 私密报告，勿公开 Issue

当前处于早期开发阶段（🚧 Early Development / MVP），重点关注：领域模型设计、员工能力模型、AI Project Planner、Resource Solver、MVP 闭环。

---

## License

本项目基于 [MIT License](LICENSE) 开源。

---

**AI Resource Orchestrator**

> From project intent to workforce allocation.
