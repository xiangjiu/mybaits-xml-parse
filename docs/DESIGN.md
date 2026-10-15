# MyBatis XML 表关联关系解析工具 — 设计文档

> 版本：v0.1（设计稿） · 日期：2026-10-10
> 状态：设计阶段，本文档为首个交付物。

---

## 1. 项目背景与目标

### 1.1 痛点
接手一个陌生的 Java/MyBatis 项目时，数据模型分散在数百个 `*Mapper.xml` 中：表与表的关联关系（JOIN、子查询、`resultMap` 的 `association`/`collection`）埋在各种动态 SQL 里，没有物理外键可循（多数生产库遵循「不用外键」规范）。新人想快速建立「表关系全景图」非常耗时，只能逐个翻 SQL。

### 1.2 目标
做一个 **Web 端工具**：传入一个项目的 MyBatis Mapper XML 目录，自动：

1. 解析每个 mapper.xml，提取所有 SQL（含动态标签拼接）；
2. 用 SQL 解析器提取涉及的表名、JOIN 类型、ON 连接字段；
3. 推理并合并表与表之间的关联关系，构建关系图（节点=表，边=关联）；
4. 以可交互的 ER 图 Web 页面呈现，支持搜索、单表详情、导出。

### 1.3 目标用户
- 接手存量项目的后端开发；
- 架构师 / DBA 做数据资产盘点；
- 技术评审时快速理解一个模块的数据模型。

### 1.4 范围边界
- **只读分析**：不连数据库、不改库、不执行 SQL；
- 输入：本地/指定目录下的 mapper.xml 文件集合；
- 输出：表关系 ER 图 + JSON 结果；
- 不做 SQL 改写、不做性能分析、不做代码生成。

---

## 2. 调研结论与借鉴来源

对 GitHub 做了多轮检索，**结论：没有现成的「MyBatis Mapper XML → 表关系图」专门工具**。这是一个未被独立工具覆盖的细分场景。但以下开源项目可「各取所需」：

| 来源 | 仓库 | 借鉴点 |
|---|---|---|
| MyBatis 核心 | [mybatis/mybatis-3](https://github.com/mybatis/mybatis-3) | `XPathParser` / `XMLMapperBuilder` 解析动态 SQL 标签的能力；动态节点 `SqlNode` 拼接思路 |
| JSqlParser | [JSQLParser/JSqlParser](https://github.com/JSQLParser/JSqlParser) | 主力 SQL 解析器：`TablesNamesFinder` 提表名、`PlainSelect.getJoins()` 提 JOIN 与 ON 条件 |
| Alibaba Druid | [alibaba/druid](https://github.com/alibaba/druid) | 多方言 SQL 解析兜底（JSqlParser 解析失败时回退） |
| mybatis-toolkit-pro | [xiyeming/mybatis-toolkit-pro](https://github.com/xiyeming/mybatis-toolkit-pro) | mapper.xml 与 Java 接口导航思路（后续可扩展） |
| MyBatisCodeHelper-Pro | [gejun123456/MyBatisCodeHelper-Pro](https://github.com/gejun123456/MyBatisCodeHelper-Pro) | IDE 侧 XML 校验/补全思路，仅参考 |
| ReactFlow | [reactflow](https://github.com/xyflow/xyflow) | 前端交互式 ER 图渲染 |
| Mermaid.js | [mermaid-js/mermaid](https://github.com/mermaid-js/mermaid) | 导出文本 ER 图（轻量分享） |

> 各取所需：**MyBatis 借 XML/动态解析，JSqlParser 借 SQL→关系提取，ReactFlow 借可视化**。三者拼成本工具核心链路。

---

## 3. 整体架构

前后端分离，后端无状态扫描，前端独立部署。

```
┌─────────────────────────────────────────────────────────────┐
│  输入：项目 mapper.xml 目录                                   │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│  后端 SpringBoot 2.7 (Java 8)                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ XML 扫描+解析 │→│ 动态SQL拼接    │→│ JSqlParser 解析   │  │
│  │ (MyBatis能力) │  │(if/foreach展开)│  │ (表名/JOIN/ON)  │  │
│  └──────────────┘  └──────────────┘  └────────┬─────────┘  │
│                                                ▼            │
│                                      ┌──────────────────┐    │
│                                      │ 关系图构建(去重合并)│   │
│                                      └────────┬─────────┘    │
│                                               ▼             │
│                                      ┌──────────────────┐    │
│                                      │ REST API (JSON)  │    │
│                                      └────────┬─────────┘    │
└───────────────────────────────────────────────┼──────────────┘
                                                ▼
┌─────────────────────────────────────────────────────────────┐
│  前端 Vite + React 18 + TS                                    │
│   ReactFlow ER图 ┃ 表列表/搜索 ┃ 单表详情 ┃ 导出(Mermaid/PNG)  │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. 核心模块设计

### 4.1 XML 扫描与解析模块 `analyzer/mapper`

**职责**：找到所有 mapper.xml，提取每条 SQL 语句的「代表性完整文本」。

- **XmlScanner**：递归扫描 `baseDir` 下所有 `*Mapper.xml`，支持 glob `classpath*:mapper/**/*.xml` 风格的相对路径。
- **节点解析**：解析 `<select>/<insert>/<update>/<delete>`，提取 `id`、`namespace`、`resultType`/`resultMap`、SQL 文本节点。
- **`<include>`/`<sql>` 处理**：`IncludeResolver` 把 `<include refid="xxx">` 展开为对应 `<sql>` 片段，递归处理嵌套 include。
- **动态 SQL 拼接** `DynamicSqlFlattener`：
  - `<if test="...">`：取**为真**分支（贪心，目的是拼出一条可解析的代表性 SQL）；
  - `<where>`/`<set>`/`<trim>`：去掉首尾 `AND/OR/逗号` 后取内容；
  - `<choose>`：取第一个 `<when>` 分支（无则 `<otherwise>`）；
  - `<foreach>`：展开为**单个占位项**（如 `IN (?)`），保留语义即可；
  - 拼接产出「代表性 SQL」用于结构解析，**同时保留原始 XML 片段**供前端展示与溯源。
- 占位符预处理：`#{xxx}` / `${xxx}` 替换为占位字面量（如 `?` 或 `:p`），避免 JSqlParser 因 `#{}` 报错。

> 注：贪心拼接不保证 SQL 业务语义 100% 准确，但足以提取「表与表何时一起出现」的结构信息。解析失败的单条 SQL 降级处理（见 §6）。

### 4.2 SQL 解析模块 `analyzer/sql`

**职责**：用 JSqlParser 从代表性 SQL 中提取表与关联。

- **SqlTableExtractor**：
  - `TablesNamesFinder.getTableList(stmt)` → 该 SQL 涉及的全部表名（主表 + JOIN 表 + 子查询表）。
  - `stmt instanceof PlainSelect` 时，遍历 `getJoins()`：
    - `join.getRightItem()` → 右表名（子查询则递归）；
    - `join.isLeft()/isRight()/isInner()/isFull()` → JOIN 类型；
    - `join.getOnConditions()` → ON 条件，解析等值连接 `a.col = b.col` 得到「关联字段对」。
  - `FromItem` 为主表；子查询（`SubSelect`）递归提取其内部表。
- **别名映射**：维护 `alias → 真实表名` 映射，ON 条件中的 `a.user_id` 解析回真实表+字段。
- **降级链**：JSqlParser 抛异常 → 回退 Druid 解析 → 仍失败 → 正则抓 `FROM/JOIN` 后的表名（仅记表出现，不记关联字段）。

### 4.3 关系推理模块 `analyzer/relation`

**职责**：把分散的提取结果合并成一张关系图。

- **TableNode**：`name`、`mappers[]`（出现于哪些 mapper）、`sqlCount`（出现次数）、`fields?[]`（可选，如能从 resultMap 推出）。
- **TableEdge**：`left`、`right`、`joinType`、`onFields[]`（等值连接字段对）、`sources[]`（来源 SQL 片段 + 所在 mapper + 语句 id）、`strength`（显式 JOIN=强 / association 暗示=弱）。
- **RelationGraphBuilder**：
  - 以 `(left, right)` 为 key 合并同一对表的多次 JOIN：累加 `onFields`、记多个 `sources`、保留出现过的最强 JOIN 类型；
  - 解析 `<resultMap>` 的 `<association>/<collection>`：若能由 `javaType`/`ofType` 映射到表（需配合实体类→表名映射，或同 resultMap 字段名启发），补一条「弱关联」边，关联字段用 `column`/`property` 启发，标注为疑似；
  - 输出 `AnalyzeResult`。

### 4.4 API 层 `web/api`

异步任务模型，避免大项目扫描阻塞。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/analyze` | 入参 `{ baseDir }` 或 `{ mapperDirs: [...] }`；创建异步任务，返回 `{ analyzeId }` |
| GET | `/api/progress/{analyzeId}` | 返回 `{ status, processed, total, currentFile }` |
| GET | `/api/result/{analyzeId}` | 返回完整 `AnalyzeResult`（表节点 + 边 + 摘要） |
| GET | `/api/table/{analyzeId}/{tableName}` | 单表详情：参与的 SQL 列表、关联表、字段 |
| GET | `/api/export/{analyzeId}?format=mermaid` | 导出 Mermaid ER 文本 |

任务结果内存缓存（`ConcurrentHashMap`），可后续接 Redis 持久化。

### 4.5 前端可视化 `frontend`

- **技术**：Vite + React 18 + TS + ReactFlow + dagre（自动布局）。
- **ErdCanvas**：ReactFlow 画布，节点=表卡片（表名 + 字段折叠列表），边=关联（label 显示 `JOIN类型 + 关联字段`），支持拖拽/缩放/框选。
- **TableList**（左栏）：搜索框 + 表名列表，点击定位画布对应节点。
- **TableDetail**（右栏）：选中表后展示参与的 SQL 片段（高亮该表名）、关联表清单。
- **Toolbar**：重新分析、导出 Mermaid/PNG、布局刷新、按 JOIN 类型过滤边。
- 自动布局：`dagre` 计算层级坐标后灌入 ReactFlow 节点 position。

---

## 5. 数据模型（JSON）

```jsonc
// TableNode
{
  "name": "t_user",
  "mappers": ["com.xxx.UserMapper", "com.xxx.OrderMapper"],
  "sqlCount": 12,
  "fields": ["id", "name", "dept_id"]   // 可选，来自 resultMap
}

// TableEdge
{
  "left": "t_order",
  "right": "t_user",
  "joinType": "LEFT",
  "onFields": [{ "leftField": "user_id", "rightField": "id" }],
  "sources": [
    { "mapper": "com.xxx.OrderMapper", "statementId": "selectOrders", "sql": "SELECT ... LEFT JOIN t_user u ON o.user_id = u.id" }
  ],
  "strength": "STRONG"   // STRONG=显式JOIN, WEAK=association暗示
}

// AnalyzeResult
{
  "tables": [ /* TableNode[] */ ],
  "edges": [ /* TableEdge[] */ ],
  "summary": { "tableCount": 23, "edgeCount": 31, "mapperCount": 14 }
}
```

---

## 6. 关键技术难点与对策

| 难点 | 对策 |
|---|---|
| 动态 SQL 无法静态确定真实分支 | 贪心取「所有 `<if>` 为真」拼代表性 SQL；解析失败降级正则抓 `FROM/JOIN` 表名 |
| `#{}`/`${}` 占位干扰 SQL 解析 | 解析前统一替换为占位字面量 `?` |
| 多方言 / 自定义函数 / 存储过程 | JSqlParser 失败 → Druid 兜底 → 正则兜底，三级降级 |
| `<resultMap>` association/collection 暗示的关联无显式 ON | 解析 `column`/`property`，标注「弱关联 / 疑似」，关联字段尽力启发 |
| 同名表别名冲突（`a`/`b` 多处复用） | 按 SQL 内 `alias→真实表` 局部映射解析后再全局合并 |
| 超大项目扫描耗时 | 异步任务 + 进度上报 + 支持限定子目录；单文件解析失败不中断整体 |
| `<include>` 跨文件引用 | 先全局收集所有 `<sql id>`，再解析 include；refid 解析不到则保留原样并告警 |
| 表名带 schema 前缀（`db.t_user`） | 归一化：去前缀按短表名聚合，保留全名用于展示 |

---

## 7. 后端目录结构（后续实现参考）

```
backend/
  pom.xml
  src/main/java/com/xxx/mybatisrel/
    analyzer/
      mapper/   XmlScanner, MapperXmlParser, IncludeResolver, DynamicSqlFlattener
      sql/      SqlTableExtractor (JSqlParser), DruidFallbackParser, RegexFallbackParser
      relation/ RelationGraphBuilder, TableNode, TableEdge, AnalyzeResult
    web/
      api/      AnalyzeController
      service/  AnalyzeService, AnalyzeTaskManager
      dto/      AnalyzeRequest, ProgressResponse
    config/     (异步线程池等)
frontend/
  package.json, vite.config.ts, tsconfig.json
  src/
    main.tsx, App.tsx
    components/ ErdCanvas.tsx, TableList.tsx, TableDetail.tsx, Toolbar.tsx
    api/        client.ts
    types/      result.ts
docs/
  DESIGN.md     ← 本文档（本次交付）
```

---

## 8. API 设计（请求/响应示例）

### 8.1 发起分析

```
POST /api/analyze
Content-Type: application/json

{ "baseDir": "D:/projects/foo/src/main/resources/mapper" }
```

```json
{ "analyzeId": "a1b2c3", "status": "QUEUED" }
```

### 8.2 查询进度

```
GET /api/progress/a1b2c3
```

```json
{ "status": "RUNNING", "processed": 8, "total": 14, "currentFile": "OrderMapper.xml" }
```

### 8.3 获取结果

```
GET /api/result/a1b2c3
```

```json
{
  "tables": [
    { "name": "t_user", "mappers": ["com.xxx.UserMapper"], "sqlCount": 5, "fields": ["id","name"] },
    { "name": "t_order", "mappers": ["com.xxx.OrderMapper"], "sqlCount": 8, "fields": ["id","user_id"] }
  ],
  "edges": [
    { "left": "t_order", "right": "t_user", "joinType": "LEFT",
      "onFields": [{"leftField":"user_id","rightField":"id"}],
      "sources": [{"mapper":"com.xxx.OrderMapper","statementId":"selectOrders",
        "sql":"SELECT * FROM t_order o LEFT JOIN t_user u ON o.user_id = u.id"}],
      "strength": "STRONG" }
  ],
  "summary": { "tableCount": 2, "edgeCount": 1, "mapperCount": 2 }
}
```

### 8.4 导出 Mermaid

```
GET /api/export/a1b2c3?format=mermaid
```

```
erDiagram
  t_order ||--o{ t_user : "user_id=id (LEFT)"
```

---

## 9. 实施里程碑（后续编码阶段参考）

1. **M1 XML 扫描 + 动态 SQL 拼接** ✅：递归扫描 + `<if>/<where>/<set>/<choose>/<foreach>/<include>` 展平 + `#{}/${}` 占位归一。
2. **M2 JSqlParser 表名/JOIN/ON 提取** ✅：`PlainSelect.getJoins()`、ON 等值连接字段、子查询递归；别名→真实表映射；无向合并。
3. **M3 SpringBoot REST + 异步任务** ✅：`/api/analyze`、`/progress`、`/result`、`/table`、`/export`。
4. **M4 ReactFlow 前端可视化** ✅：dagre 自动布局 + 表卡片节点 + 表列表 + 单表详情 + Mermaid 导出。
5. **M5 关联字段启发增强** ✅：所有 ON 字段对入边并入两端表节点；从 SELECT 列与 WHERE 等值条件发现字段填充 TableNode.fields。
6. **M6 鲁棒性** ✅：JSqlParser → Druid（多方言）→ 正则 三级降级；`<resultMap>` 的 association/collection 经 `select`/`resultMap` 引用推断 WEAK 弱关联边（与 STRONG 合并，强关联优先）。

---

## 10. 验证方式（后续实现后执行）

- **样例 fixture**：构造一个含 `association`/`collection`、`<if>`/`<foreach>` 动态 SQL、子查询、多 JOIN 类型的示例 mapper.xml。
- **后端断言**：调用 `/api/analyze` 后取 `/api/result`，断言：表数量正确、JOIN 类型正确、ON 字段对正确、弱关联边被标注。
- **降级验证**：故意写一段 JSqlParser 解析不了的 SQL，验证 Druid/正则兜底仍能提表名。
- **前端人工校验**：打开页面，肉眼核对 ER 图与样例 SQL 一致；导出 Mermaid 文本可被渲染。
- **性能基线**：用真实项目（如 RuoYi）mapper 目录跑一遍，记录耗时与表/边数量。
