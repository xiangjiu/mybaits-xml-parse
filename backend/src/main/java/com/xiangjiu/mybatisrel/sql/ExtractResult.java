package com.xiangjiu.mybatisrel.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单条 SQL 的提取结果中间结构。
 */
public class ExtractResult {

    /** 该 SQL 涉及的短表名集合 */
    public Set<String> tables = new LinkedHashSet<>();
    /** 提取到的关联（含 JOIN 与子查询关联） */
    public List<ExtractRelation> relations = new ArrayList<>();
    /** JSqlParser 是否解析失败（true 则走正则兜底，仅记表名） */
    public boolean parseFailed = false;

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
