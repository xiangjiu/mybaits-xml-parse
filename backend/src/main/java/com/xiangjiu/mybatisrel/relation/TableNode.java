package com.xiangjiu.mybatisrel.relation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 表节点：ER 图中的一个表。
 */
public class TableNode {

    /** 短表名（去 schema 前缀后），作为聚合 key */
    private String name;
    /** 出现过的全名（含 schema 前缀），用于展示 */
    private Set<String> fullNames = new LinkedHashSet<>();
    /** 该表出现于哪些 mapper（namespace） */
    private Set<String> mappers = new LinkedHashSet<>();
    /** 该表参与的 SQL 条数 */
    private int sqlCount;
    /** 字段（可选，来自 resultMap / 关联条件启发） */
    private Set<String> fields = new LinkedHashSet<>();

    public TableNode(String name) {
        this.name = name;
    }

    public void addField(String field) {
        if (field != null && !field.isEmpty()) {
            fields.add(field);
        }
    }

    public void incSqlCount() {
        sqlCount++;
    }

    // 为 JSON 序列化提供稳定的列表
    public List<String> getMappersList() {
        return new ArrayList<>(mappers);
    }

    public List<String> getFieldsList() {
        return new ArrayList<>(fields);
    }

    public List<String> getFullNamesList() {
        return new ArrayList<>(fullNames);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<String> getFullNames() { return fullNames; }
    public Set<String> getMappers() { return mappers; }
    public int getSqlCount() { return sqlCount; }
    public void setSqlCount(int sqlCount) { this.sqlCount = sqlCount; }
    public Set<String> getFields() { return fields; }
}
