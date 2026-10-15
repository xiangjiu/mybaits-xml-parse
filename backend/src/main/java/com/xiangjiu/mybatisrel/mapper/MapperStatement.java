package com.xiangjiu.mybatisrel.mapper;

/**
 * 从一个 mapper.xml 中解析出的一条语句。
 */
public class MapperStatement {

    private String namespace;
    private String statementId;
    /** select / insert / update / delete */
    private String type;
    private String resultMap;
    private String resultType;
    /** 动态标签展开后的代表性 SQL（#{}/${} 已替换为占位） */
    private String flatSql;
    /** 原始内层 XML 文本，用于前端展示与溯源 */
    private String rawXml;
    /** 来源文件名 */
    private String fileName;

    public MapperStatement() {}

    public String getFullName() {
        return namespace + "." + statementId;
    }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getStatementId() { return statementId; }
    public void setStatementId(String statementId) { this.statementId = statementId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getResultMap() { return resultMap; }
    public void setResultMap(String resultMap) { this.resultMap = resultMap; }
    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }
    public String getFlatSql() { return flatSql; }
    public void setFlatSql(String flatSql) { this.flatSql = flatSql; }
    public String getRawXml() { return rawXml; }
    public void setRawXml(String rawXml) { this.rawXml = rawXml; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
