# Contributing / 参与贡献

English | [简体中文](#简体中文)

Thanks for your interest in contributing to **AI Resource Orchestrator**! This project is in the early MVP stage — issues, discussions, docs, and code contributions are all welcome.

## How to Contribute

1. **Report a bug or propose a feature**: open an [issue](https://github.com/hx346/ai-resource-orchestrator/issues) and use the issue templates.
2. **Fork & branch**: fork the repo, create a branch from `main`:
   ```bash
   git checkout -b feat/your-feature
   ```
3. **Develop locally**:
   ```bash
   ./mvnw spring-boot:run        # backend (JDK 25, PostgreSQL 16+)
   cd web && pnpm install && pnpm dev   # frontend
   ```
4. **Commit**: use concise conventional commit messages (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
5. **Open a Pull Request** against `main`, describe the change, and link related issues.

## Guidelines

- Keep changes minimal and focused — one PR addresses one concern.
- Follow existing code style (backend: Alibaba Java Coding Guidelines; frontend: project ESLint config).
- Database changes must go through Flyway migrations (`src/main/resources/db/migration/`), never manual DDL.
- Update documentation (`README.md` / `README.en.md`) when behavior changes — keep both languages in sync.
- AI-related code: business validation, persistence, permissions, and transactions stay in business modules; the AI module only understands/generates/infers/explains.
- Be respectful and follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting Security Issues

Please follow [SECURITY.md](SECURITY.md) — do not open public issues for vulnerabilities.

---

# 简体中文

感谢关注 **AI Resource Orchestrator**！项目处于 MVP 早期阶段，欢迎提交 Issue、参与讨论、完善文档和贡献代码。

## 贡献流程

1. **反馈 Bug 或提出功能**：提交 [Issue](https://github.com/hx346/ai-resource-orchestrator/issues)，请使用 Issue 模板。
2. **Fork 并创建分支**：从 `main` 创建分支：
   ```bash
   git checkout -b feat/your-feature
   ```
3. **本地开发**：
   ```bash
   ./mvnw spring-boot:run        # 后端（JDK 25、PostgreSQL 16+）
   cd web && pnpm install && pnpm dev   # 前端
   ```
4. **提交**：使用简洁的约定式提交信息（`feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`）。
5. **发起 Pull Request** 到 `main`，描述改动内容并关联相关 Issue。

## 贡献规范

- 改动保持最小且聚焦——一个 PR 只解决一件事。
- 遵循现有代码风格（后端：阿里巴巴 Java 开发手册；前端：项目 ESLint 配置）。
- 数据库变更必须通过 Flyway 迁移脚本（`src/main/resources/db/migration/`），禁止手工 DDL。
- 行为变更时同步更新文档（`README.md` / `README.en.md`），保持中英文一致。
- AI 相关代码：业务校验、持久化、权限、事务放在业务模块；AI 模块只负责理解、生成、推断、解释。
- 尊重社区成员，遵守[行为准则](CODE_OF_CONDUCT.md)。

## 安全问题反馈

请遵循 [SECURITY.md](SECURITY.md)，安全漏洞不要公开 Issue。
