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

