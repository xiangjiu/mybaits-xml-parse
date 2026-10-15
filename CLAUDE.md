# CLAUDE.md

本项目指南，给 Claude（及协作者）的项目级约定。优先级高于默认行为。

## 项目简介

MyBatis XML 表关联关系解析工具：扫描一个项目的 MyBatis Mapper XML，提取 SQL 中涉及的表与 JOIN 关联，以 Web 端 ER 图可视化呈现。设计文档见 `docs/DESIGN.md`。

- 后端：Spring Boot 2.7 + Java 8 + Maven（JSqlParser 解析 SQL）
- 前端：Vite + React 18 + TypeScript（ReactFlow + dagre 渲染 ER 图）

## 工作要求（用户约定）

### 后端：只编译，不测试
- 后端改动**只需保证编译通过**（`mvn -q compile`），**不要运行测试**，不要为后端编写/运行 JUnit 测试，除非用户明确要求。
- 前端可用 `npx tsc -b` + `npx vite build` 做类型检查与构建验证。

### 提交与推送
- **没有用户的明确指令，不得 `git push`。** 完成改动后可做本地 `git add`/`commit`，然后告知用户「已提交到本地，是否推送？」，等明确答复再 push。
- 远程用 SSH 推送（已配置）。
- 破坏性操作（force push、改写历史、删远程分支）需单独确认。

## 本机工具链（已探明，勿重复探测）

| 工具 | 版本 |
|---|---|
| Java / JDK | 1.8.0_151（Java 8） |
| Maven | 3.9.16 |
| Node | v22.22.3 |
| npm | 10.9.8 |
| OS | Windows 11，Shell 用 Git Bash |

> 详细的版本/路径与 SSH 凭据信息存于 Claude 本地记忆（不进仓库），需要时查记忆，勿重复 `--version` 探测。

## 构建与运行

```bash
# 后端（mvn 不在默认 PATH 时用本地 Maven 或包装器）
cd backend && mvn -q compile          # 仅编译验证
cd backend && mvn spring-boot:run      # 运行，端口 8080

# 前端
cd frontend && npm install
cd frontend && npm run dev             # 端口 5173，已代理 /api → 8080
cd frontend && npx tsc -b && npx vite build   # 类型检查 + 构建
```

## 模块速览

- `backend/.../mapper/` — XML 扫描 + 动态 SQL 展平（`XmlScanner`、`MapperXmlParser`、`DynamicSqlFlattener`）
- `backend/.../sql/` — JSqlParser 提取表/JOIN/ON（`SqlTableExtractor`，含正则降级）
- `backend/.../relation/` — 关系图构建（`RelationGraphBuilder`、`TableNode`、`TableEdge`）
- `backend/.../web/` — REST API + 异步任务（`AnalyzeController`、`AnalyzeService`、`AnalyzeTaskManager`）
- `frontend/src/` — React 应用（`ErdCanvas` ReactFlow 画布、`TableList`、`TableDetail`）

## 里程碑

- [x] M1 XML 扫描 + 动态 SQL 拼接
- [x] M2 JSqlParser 表名/JOIN/ON 提取 + 正则降级
- [x] M3 SpringBoot REST + 异步任务 + 进度
- [x] M4 ReactFlow 前端 ER 图 + 表列表 + 单表详情 + Mermaid 导出
- [ ] M5 关联字段启发增强
- [ ] M6 Druid 多方言兜底、resultMap association/collection 弱关联、性能优化
