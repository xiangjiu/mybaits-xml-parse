# MyBatis XML 表关联关系解析工具

扫描一个项目的 MyBatis Mapper XML，提取 SQL 中涉及的表与 JOIN 关联，以 Web 端 ER 图可视化呈现，帮助快速上手陌生项目。

详细设计见 [docs/DESIGN.md](docs/DESIGN.md)。

## 技术栈

- 后端：Spring Boot 2.7 + Java 8 + Maven（JSqlParser 解析 SQL）
- 前端：Vite + React 18 + TypeScript（ReactFlow + dagre 渲染 ER 图）

## 运行

### 后端

```bash
cd backend
# 本机 mvn 不在 PATH 时用包装器或加 PATH
mvn spring-boot:run
# 默认端口 8080
```

### 前端

```bash
cd frontend
npm install
npm run dev
# 默认端口 5173，已代理 /api → 8080
```

打开 http://localhost:5173 ，输入 mapper.xml 所在目录，点「分析」。

## 工作链路

```
mapper.xml → DOM 解析 + 动态 SQL 展平 → JSqlParser 提取表/JOIN/ON
         → 合并关系图(节点=表, 边=关联) → REST JSON → ReactFlow ER 图
```

## 进度

- [x] M1 XML 扫描 + 动态 SQL 拼接（`<if>/<where>/<set>/<choose>/<foreach>/<include>`）
- [x] M2 JSqlParser 表名/JOIN/ON 提取 + 正则降级
- [x] M3 SpringBoot REST + 异步任务 + 进度
- [x] M4 ReactFlow 前端 ER 图 + 表列表 + 单表详情 + Mermaid 导出
- [x] M5 关联字段启发增强（ON 字段全量入节点 + SELECT/WHERE 字段发现）
- [x] M6 Druid 多方言兜底 + resultMap association/collection 弱关联边
