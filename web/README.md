# Web Frontend（前端）

> 🚧 Placeholder — 将在 MVP 阶段初始化 / To be scaffolded during the MVP phase.

## 技术栈 / Tech Stack

```text
Vue 3
TypeScript
Vben Admin
Ant Design Vue
Vite
pnpm
```

## 计划页面 / Planned Pages

```text
首页（"今天准备开展什么项目？"入口）
员工管理
技能库
员工能力画像
项目管理
AI项目规划
资源编排
系统设置
```

## 本地开发 / Local Development

后端就绪后（见仓库根 README「快速开始」）：

```bash
cd web
pnpm install
pnpm dev
```

## 说明 / Notes

- 前端将通过 `/api/v1` 调用后端 REST API，AI 规划流式输出使用 SSE。
- 所有 AI 生成结果（WBS / Task / Skill / Resource Plan）在前端均须可编辑。
