package com.xiangjiu.mybatisrel.relation;

import com.xiangjiu.mybatisrel.mapper.MapperStatement;
import com.xiangjiu.mybatisrel.sql.ExtractResult;
import com.xiangjiu.mybatisrel.sql.SqlTableExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把扫描得到的 MapperStatement 列表合并成表关系图。
 */
public class RelationGraphBuilder {

    private final SqlTableExtractor extractor = new SqlTableExtractor();

    public AnalyzeResult build(List<MapperStatement> statements) {
        AnalyzeResult result = new AnalyzeResult();
        Map<String, TableNode> nodeMap = new LinkedHashMap<>();
        Map<String, TableEdge> edgeMap = new LinkedHashMap<>();

        int statementCount = 0;
        int parseFailCount = 0;
        int mapperCount = 0;
        java.util.Set<String> seenMappers = new java.util.LinkedHashSet<>();

        for (MapperStatement ms : statements) {
            statementCount++;
            seenMappers.add(ms.getNamespace());
            ExtractResult ext = extractor.extract(ms.getFlatSql());
            if (ext.parseFailed) {
                parseFailCount++;
            }

            // 表节点
            for (String table : ext.tables) {
                TableNode node = nodeMap.computeIfAbsent(table, TableNode::new);
                node.getMappers().add(ms.getNamespace());
                node.getFullNames().add(table);
                node.incSqlCount();
            }

            // 关联边
            for (ExtractResult.ExtractRelation rel : ext.relations) {
                String left = rel.left;
                String right = rel.right;
                if (left == null || right == null || left.equalsIgnoreCase(right)) {
                    continue;
                }
                // 规范化方向（无向）：key 用字典序小的在前
                boolean swap = left.compareToIgnoreCase(right) > 0;
                String a = swap ? right : left;
                String b = swap ? left : right;
                String key = a + "|" + b;
                TableEdge edge = edgeMap.computeIfAbsent(key,
                        k -> new TableEdge(a, b, rel.joinType, "STRONG"));
                edge.setJoinType(TableEdge.stronger(edge.getJoinType(), rel.joinType));
                for (ExtractResult.OnFieldPair pair : rel.onFields) {
                    String lf = swap ? pair.rightField : pair.leftField;
                    String rf = swap ? pair.leftField : pair.rightField;
                    edge.addOnField(new OnField(lf, rf));
                }
                edge.addSource(new SqlSource(ms.getNamespace(), ms.getStatementId(), ms.getFlatSql()));

                // 字段启发：把关联字段塞进两端的表节点
                TableNode na = nodeMap.get(a);
                TableNode nb = nodeMap.get(b);
                String lf = swap ? pairFieldRight(rel) : pairFieldLeft(rel);
                String rf = swap ? pairFieldLeft(rel) : pairFieldRight(rel);
                if (na != null) na.addField(lf);
                if (nb != null) nb.addField(rf);
            }
        }

        mapperCount = seenMappers.size();

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

    private String pairFieldLeft(ExtractResult.ExtractRelation rel) {
        return rel.onFields.isEmpty() ? null : rel.onFields.get(0).leftField;
    }

    private String pairFieldRight(ExtractResult.ExtractRelation rel) {
        return rel.onFields.isEmpty() ? null : rel.onFields.get(0).rightField;
    }
}
