package com.xiangjiu.mybatisrel.relation;

/**
 * 一条关联边的来源：某条 SQL 在某个 mapper 的某个语句 id。
 */
public class SqlSource {

    private String mapper;
    private String statementId;
    /** 代表性 SQL 文本（动态标签已展开） */
    private String sql;

    public SqlSource() {}

    public SqlSource(String mapper, String statementId, String sql) {
        this.mapper = mapper;
        this.statementId = statementId;
        this.sql = sql;
    }

    public String getMapper() { return mapper; }
    public void setMapper(String mapper) { this.mapper = mapper; }
    public String getStatementId() { return statementId; }
    public void setStatementId(String statementId) { this.statementId = statementId; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
}
