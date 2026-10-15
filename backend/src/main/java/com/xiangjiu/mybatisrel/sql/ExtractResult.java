package com.xiangjiu.mybatisrel.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单条 SQL 的提取结果中间结构。
 */
public class ExtractResult {

    /** 该 SQL 涉及的短表名集合 */
    public Set<String> tables = new LinkedHashSet<>();
    /** 该 SQL 的主表（FROM 主表 / INSERT/UPDATE/DELETE 目标表） */
    public String mainTable;
    /** 启发发现的表→字段（来自 SELECT 列与 WHERE 等值条件） */
    public Map<String, Set<String>> columns = new LinkedHashMap<>();
    /** 提取到的关联（含 JOIN 与子查询关联） */
    public List<ExtractRelation> relations = new ArrayList<>();
    /** JSqlParser 是否解析失败（true 则走 Druid/正则兜底，仅记表名） */
    public boolean parseFailed = false;

    public void addColumn(String table, String field) {
        if (table == null || field == null || field.isEmpty()) {
            return;
        }
        columns.computeIfAbsent(table, k -> new LinkedHashSet<>()).add(field);
    }

    public static class ExtractRelation {
        public String left;
        public String right;
        public String joinType;
        public List<OnFieldPair> onFields = new ArrayList<>();

        public ExtractRelation(String left, String right, String joinType) {
            this.left = left;
            this.right = right;
            this.joinType = joinType;
        }
    }

    public static class OnFieldPair {
        public String leftField;
        public String rightField;
        public OnFieldPair(String leftField, String rightField) {
            this.leftField = leftField;
            this.rightField = rightField;
        }
    }
}
