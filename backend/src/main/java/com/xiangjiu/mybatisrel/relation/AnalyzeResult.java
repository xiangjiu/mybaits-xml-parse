package com.xiangjiu.mybatisrel.relation;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次分析的完整结果。
 */
public class AnalyzeResult {

    private List<TableNode> tables = new ArrayList<>();
    private List<TableEdge> edges = new ArrayList<>();
    private Summary summary = new Summary();

    public List<TableNode> getTables() { return tables; }
    public void setTables(List<TableNode> tables) { this.tables = tables; }
    public List<TableEdge> getEdges() { return edges; }
    public void setEdges(List<TableEdge> edges) { this.edges = edges; }
    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    public static class Summary {
        private int tableCount;
        private int edgeCount;
        private int mapperCount;
        private int statementCount;
        private int parseFailCount;

        public int getTableCount() { return tableCount; }
        public void setTableCount(int tableCount) { this.tableCount = tableCount; }
        public int getEdgeCount() { return edgeCount; }
        public void setEdgeCount(int edgeCount) { this.edgeCount = edgeCount; }
        public int getMapperCount() { return mapperCount; }
        public void setMapperCount(int mapperCount) { this.mapperCount = mapperCount; }
        public int getStatementCount() { return statementCount; }
        public void setStatementCount(int statementCount) { this.statementCount = statementCount; }
        public int getParseFailCount() { return parseFailCount; }
        public void setParseFailCount(int parseFailCount) { this.parseFailCount = parseFailCount; }
    }
}
