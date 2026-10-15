package com.xiangjiu.mybatisrel.web.dto;

import com.xiangjiu.mybatisrel.relation.OnField;
import com.xiangjiu.mybatisrel.relation.SqlSource;
import com.xiangjiu.mybatisrel.relation.TableEdge;
import com.xiangjiu.mybatisrel.relation.TableNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 单表详情：表节点 + 该表参与的关联边 + 涉及的 SQL 来源。
 */
public class TableDetail {

    private TableNode table;
    private List<RelatedEdge> relatedEdges = new ArrayList<>();
    private List<SqlSource> sqlSources = new ArrayList<>();

    public TableNode getTable() { return table; }
    public void setTable(TableNode table) { this.table = table; }
    public List<RelatedEdge> getRelatedEdges() { return relatedEdges; }
    public List<SqlSource> getSqlSources() { return sqlSources; }

    public static class RelatedEdge {
        public String otherTable;
        public String joinType;
        public List<OnField> onFields;
        public String strength;
        public RelatedEdge(String otherTable, String joinType, List<OnField> onFields, String strength) {
            this.otherTable = otherTable;
            this.joinType = joinType;
            this.onFields = onFields;
            this.strength = strength;
        }
        public String getOtherTable() { return otherTable; }
        public String getJoinType() { return joinType; }
        public List<OnField> getOnFields() { return onFields; }
        public String getStrength() { return strength; }
    }
}
