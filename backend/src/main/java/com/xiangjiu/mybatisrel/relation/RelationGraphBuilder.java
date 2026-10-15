package com.xiangjiu.mybatisrel.relation;

import com.xiangjiu.mybatisrel.mapper.Association;
import com.xiangjiu.mybatisrel.mapper.MapperStatement;
import com.xiangjiu.mybatisrel.mapper.ResultMapInfo;
import com.xiangjiu.mybatisrel.sql.ExtractResult;
import com.xiangjiu.mybatisrel.sql.SqlTableExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把扫描得到的 MapperStatement + ResultMapInfo 合并成表关系图。
 *
 * <p>关联来源：
 * <ul>
 *   <li>STRONG：SQL 中显式 JOIN / 子查询关联</li>
 *   <li>WEAK：resultMap 的 association/collection 通过 select 或 resultMap 暗示的关联</li>
 * </ul>
 */
public class RelationGraphBuilder {

    private final SqlTableExtractor extractor = new SqlTableExtractor();

    public AnalyzeResult build(List<MapperStatement> statements) {
        return build(statements, new ArrayList<>());
    }

    public AnalyzeResult build(List<MapperStatement> statements, List<ResultMapInfo> resultMaps) {
        AnalyzeResult result = new AnalyzeResult();
        Map<String, TableNode> nodeMap = new LinkedHashMap<>();
        Map<String, TableEdge> edgeMap = new LinkedHashMap<>();
        Map<String, ExtractResult> extByStmt = new HashMap<>();

        int statementCount = 0;
        int parseFailCount = 0;
        java.util.Set<String> seenMappers = new java.util.LinkedHashSet<>();

        // ---- 第一遍：解析每条 SQL，建节点与 STRONG 边 ----
        for (MapperStatement ms : statements) {
            statementCount++;
            seenMappers.add(ms.getNamespace());
            ExtractResult ext = extractor.extract(ms.getFlatSql());
            if (ext.parseFailed) {
                parseFailCount++;
            }
            extByStmt.put(fullName(ms), ext);

            // 表节点
            for (String table : ext.tables) {
                TableNode node = nodeMap.computeIfAbsent(table, TableNode::new);
                node.getMappers().add(ms.getNamespace());
                node.getFullNames().add(table);
                node.incSqlCount();
            }

            // 启发字段：合并 extractor 发现的列
            for (Map.Entry<String, Set<String>> e : ext.columns.entrySet()) {
                TableNode node = nodeMap.get(e.getKey());
                if (node != null) {
                    for (String f : e.getValue()) {
                        node.addField(f);
                    }
                }
            }

            // STRONG 关联边
            for (ExtractResult.ExtractRelation rel : ext.relations) {
                String left = rel.left;
                String right = rel.right;
                if (left == null || right == null || left.equalsIgnoreCase(right)) {
                    continue;
                }
                boolean swap = left.compareToIgnoreCase(right) > 0;
                String a = swap ? right : left;
                String b = swap ? left : right;
                String key = a + "|" + b;
                TableEdge edge = edgeMap.computeIfAbsent(key,
                        k -> new TableEdge(a, b, rel.joinType, "STRONG"));
                edge.setJoinType(TableEdge.stronger(edge.getJoinType(), rel.joinType));
                // 所有 ON 字段对入边
                for (ExtractResult.OnFieldPair pair : rel.onFields) {
                    String lf = swap ? pair.rightField : pair.leftField;
                    String rf = swap ? pair.leftField : pair.rightField;
                    edge.addOnField(new OnField(lf, rf));
                    // 同步把关联字段塞进两端表节点
                    TableNode na = nodeMap.get(a);
                    TableNode nb = nodeMap.get(b);
                    if (na != null) na.addField(lf);
                    if (nb != null) nb.addField(rf);
                }
                edge.addSource(new SqlSource(ms.getNamespace(), ms.getStatementId(), ms.getFlatSql()));
            }
        }

        // ---- 第二遍：resultMap association/collection → WEAK 边 ----
        buildWeakEdges(statements, resultMaps, extByStmt, nodeMap, edgeMap);

        int mapperCount = seenMappers.size();

        List<TableNode> tables = new ArrayList<>(nodeMap.values());
        tables.sort(Comparator.comparing(TableNode::getName, String.CASE_INSENSITIVE_ORDER));
        List<TableEdge> edges = new ArrayList<>(edgeMap.values());
        edges.sort(Comparator.comparing(TableEdge::getEdgeKey, String.CASE_INSENSITIVE_ORDER));

        result.setTables(tables);
        result.setEdges(edges);
        result.getSummary().setTableCount(tables.size());
        result.getSummary().setEdgeCount(edges.size());
        result.getSummary().setMapperCount(mapperCount);
        result.getSummary().setStatementCount(statementCount);
        result.getSummary().setParseFailCount(parseFailCount);
        return result;
    }

    private void buildWeakEdges(List<MapperStatement> statements, List<ResultMapInfo> resultMaps,
                               Map<String, ExtractResult> extByStmt,
                               Map<String, TableNode> nodeMap,
                               Map<String, TableEdge> edgeMap) {
        // 语句全限定名 → 主表
        Map<String, String> stmtMainTable = new HashMap<>();
        for (MapperStatement ms : statements) {
            ExtractResult ext = extByStmt.get(fullName(ms));
            if (ext != null && ext.mainTable != null) {
                stmtMainTable.put(fullName(ms), ext.mainTable);
            }
        }

        // resultMap 全限定名 → 主表（被某条语句的 resultMap 属性引用时，取该语句主表）
        Map<String, String> resultMapToMainTable = new HashMap<>();
        for (MapperStatement ms : statements) {
            if (ms.getResultMap() == null) continue;
            String fqn = resolveFqn(ms.getResultMap(), ms.getNamespace());
            ExtractResult ext = extByStmt.get(fullName(ms));
            if (ext != null && ext.mainTable != null) {
                resultMapToMainTable.putIfAbsent(fqn, ext.mainTable);
            }
        }

        // resultMap 查找表：全限定名 + 局部 id
        Map<String, ResultMapInfo> rmByFqn = new HashMap<>();
        Map<String, ResultMapInfo> rmByLocal = new HashMap<>();
        for (ResultMapInfo rm : resultMaps) {
            rmByFqn.put(rm.fqn, rm);
            rmByLocal.put(rm.id, rm);
        }

        for (MapperStatement ms : statements) {
            if (ms.getResultMap() == null) continue;
            String rmFqn = resolveFqn(ms.getResultMap(), ms.getNamespace());
            ResultMapInfo rm = rmByFqn.get(rmFqn);
            if (rm == null) rm = rmByLocal.get(ms.getResultMap());
            if (rm == null) continue;

            String parentMain = stmtMainTable.get(fullName(ms));
            if (parentMain == null) continue;

            for (Association a : rm.associations) {
                String targetMain = resolveTargetTable(a, ms.getNamespace(), stmtMainTable, resultMapToMainTable, rmByFqn);
                if (targetMain == null || targetMain.equalsIgnoreCase(parentMain)) continue;

                boolean swap = parentMain.compareToIgnoreCase(targetMain) > 0;
                String x = swap ? targetMain : parentMain;
                String y = swap ? parentMain : targetMain;
                String key = x + "|" + y;
                // 已有 STRONG 边则不补弱关联标签
                if (edgeMap.containsKey(key) && "STRONG".equals(edgeMap.get(key).getStrength())) {
                    continue;
                }
                TableEdge edge = edgeMap.computeIfAbsent(key,
                        k -> new TableEdge(x, y, "WEAK_ASSOC", "WEAK"));
                edge.addOnField(new OnField(a.column, a.column));
                edge.addSource(new SqlSource(ms.getNamespace(), ms.getStatementId(),
                        ms.getFlatSql() != null ? ms.getFlatSql() : ""));
            }
        }
    }

    /** 解析 association 指向的目标表：优先 select→语句主表，次选 resultMap 引用。 */
    private String resolveTargetTable(Association a, String namespace,
                                      Map<String, String> stmtMainTable,
                                      Map<String, String> resultMapToMainTable,
                                      Map<String, ResultMapInfo> rmByFqn) {
        if (a.select != null && !a.select.isEmpty()) {
            String t = stmtMainTable.get(resolveFqn(a.select, namespace));
            if (t != null) return t;
            t = stmtMainTable.get(a.select);
            return t;
        }
        if (a.resultMapRef != null && !a.resultMapRef.isEmpty()) {
            String fqn = resolveFqn(a.resultMapRef, namespace);
            String t = resultMapToMainTable.get(fqn);
            if (t != null) return t;
            // 该 resultMap 本身若被某语句使用也能拿到主表
            ResultMapInfo rm = rmByFqn.get(fqn);
            if (rm != null) {
                return resultMapToMainTable.get(rm.fqn);
            }
        }
        return null;
    }

    /** 若 ref 含 '.' 视为全限定；否则拼 namespace + '.' + ref。 */
    private static String resolveFqn(String ref, String namespace) {
        if (ref == null) return null;
        if (ref.indexOf('.') >= 0) return ref;
        return namespace + "." + ref;
    }

    private static String fullName(MapperStatement ms) {
        return ms.getNamespace() + "." + ms.getStatementId();
    }
}
