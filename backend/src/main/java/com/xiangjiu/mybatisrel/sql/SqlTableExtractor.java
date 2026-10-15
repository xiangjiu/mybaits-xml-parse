package com.xiangjiu.mybatisrel.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用 JSqlParser 从一条 SQL 中提取表名、JOIN 类型与 ON 连接字段。
 * 解析失败时按 Druid → 正则 三级降级。
 */
public class SqlTableExtractor {

    private static final Pattern FROM_JOIN_TABLE =
            Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([A-Za-z_][\\w]*(?:\\.[A-Za-z_][\\w]*)*)");

    private static final String DRUID_DB = "mysql";
    private static final DbType DRUID_DBTYPE = DbType.mysql;

    private final Map<String, String> aliasToTable = new LinkedHashMap<>();

    public ExtractResult extract(String sql) {
        ExtractResult result = new ExtractResult();
        if (sql == null || sql.trim().isEmpty()) {
            return result;
        }
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            result.parseFailed = true;
            // JSqlParser 失败 → Druid 兜底 → 再正则
            if (!druidFallback(sql, result)) {
                regexFallback(sql, result);
            }
            return result;
        }
        try {
            if (stmt instanceof Select) {
                handleSelectBody(((Select) stmt).getSelectBody(), result, null);
            } else if (stmt instanceof Update) {
                handleUpdate((Update) stmt, result);
            } else if (stmt instanceof Delete) {
                handleDelete((Delete) stmt, result);
            } else if (stmt instanceof Insert) {
                handleInsert((Insert) stmt, result);
            } else {
                // 其它类型，降级
                if (!druidFallback(sql, result)) {
                    regexFallback(sql, result);
                }
            }
        } catch (Exception e) {
            // 二次兜底：结构遍历失败也降级
            result.parseFailed = true;
            if (!druidFallback(sql, result)) {
                regexFallback(sql, result);
            }
        }
        return result;
    }

    // ---- SELECT ----
    private void handleSelectBody(SelectBody body, ExtractResult result, String outerFromTable) {
        if (body instanceof PlainSelect) {
            PlainSelect ps = (PlainSelect) body;
            FromItem from = ps.getFromItem();
            String mainTable = registerFromItem(from, result);
            if (result.mainTable == null && mainTable != null) {
                result.mainTable = mainTable;
            }
            // 启发发现字段：从 SELECT 列与 WHERE 收集 Column
            List<SelectItem> items = ps.getSelectItems();
            if (items != null) {
                for (SelectItem it : items) {
                    if (it instanceof SelectExpressionItem) {
                        collectColumns(((SelectExpressionItem) it).getExpression(), result, mainTable);
                    }
                }
            }
            collectColumns(ps.getWhere(), result, mainTable);

            // 子查询 from：已递归处理
            List<Join> joins = ps.getJoins();
            if (joins != null) {
                String prevTable = mainTable;
                for (Join join : joins) {
                    String rightTable = registerFromItem(join.getRightItem(), result);
                    String joinType = joinTypeOf(join);
                    List<ExtractResult.OnFieldPair> pairs = new ArrayList<>();
                    String leftTable = parseOnCondition(join.getOnExpression(), rightTable, prevTable, pairs);
                    if (leftTable == null) {
                        leftTable = prevTable;
                    }
                    if (rightTable != null && leftTable != null && !leftTable.equals(rightTable)) {
                        ExtractResult.ExtractRelation rel = new ExtractResult.ExtractRelation(leftTable, rightTable, joinType);
                        rel.onFields.addAll(pairs);
                        result.relations.add(rel);
                    }
                    if (rightTable != null) {
                        prevTable = rightTable;
                    }
                }
            }
        } else if (body instanceof SetOperationList) {
            for (SelectBody sb : ((SetOperationList) body).getSelects()) {
                handleSelectBody(sb, result, outerFromTable);
            }
        } else if (body instanceof WithItem) {
            SubSelect sub = ((WithItem) body).getSubSelect();
            if (sub != null && sub.getSelectBody() != null) {
                handleSelectBody(sub.getSelectBody(), result, outerFromTable);
            }
        }
    }

    /** 递归收集表达式中的 Column，归到其所属表（无表限定则归 defaultTable）。 */
    private void collectColumns(Expression e, ExtractResult result, String defaultTable) {
        if (e == null) return;
        if (e instanceof Column) {
            Column c = (Column) e;
            String tbl = c.getTable() != null ? resolveAlias(c.getTable().getName()) : defaultTable;
            result.addColumn(tbl, c.getColumnName());
            return;
        }
        if (e instanceof net.sf.jsqlparser.expression.BinaryExpression) {
            net.sf.jsqlparser.expression.BinaryExpression be =
                    (net.sf.jsqlparser.expression.BinaryExpression) e;
            collectColumns(be.getLeftExpression(), result, defaultTable);
            collectColumns(be.getRightExpression(), result, defaultTable);
        }
        // 函数、子查询等不深挖
    }

    // ---- UPDATE ----
    private void handleUpdate(Update upd, ExtractResult result) {
        Table t = upd.getTable();
        if (t != null) {
            registerTable(t, result);
            if (result.mainTable == null) {
                result.mainTable = shortName(t);
            }
        }
        // 4.7 的 Update 可能有 fromItems / joins
        try {
            List<Join> joins = upd.getJoins();
            if (joins != null) {
                for (Join j : joins) {
                    String right = registerFromItem(j.getRightItem(), result);
                    if (right != null && t != null) {
                        result.relations.add(new ExtractResult.ExtractRelation(shortName(t), right, joinTypeOf(j)));
                    }
                }
            }
        } catch (NoSuchMethodError | Exception ignored) {
            // 不同版本 API 差异，忽略
        }
    }

    // ---- DELETE ----
    private void handleDelete(Delete del, ExtractResult result) {
        Table t = del.getTable();
        if (t != null) {
            registerTable(t, result);
            if (result.mainTable == null) {
                result.mainTable = shortName(t);
            }
        }
        try {
            List<Join> joins = del.getJoins();
            if (joins != null) {
                for (Join j : joins) {
                    String right = registerFromItem(j.getRightItem(), result);
                    if (right != null && t != null) {
                        result.relations.add(new ExtractResult.ExtractRelation(shortName(t), right, joinTypeOf(j)));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ---- INSERT ----
    private void handleInsert(Insert ins, ExtractResult result) {
        Table t = ins.getTable();
        if (t != null) {
            registerTable(t, result);
            if (result.mainTable == null) {
                result.mainTable = shortName(t);
            }
        }
        Select select = ins.getSelect();
        if (select != null) {
            handleSelectBody(select.getSelectBody(), result, null);
        }
    }

    // ---- from item 注册（表 / 子查询）----
    private String registerFromItem(FromItem fi, ExtractResult result) {
        if (fi == null) return null;
        if (fi instanceof Table) {
            return registerTable((Table) fi, result);
        } else if (fi instanceof SubSelect) {
            SelectBody sb = ((SubSelect) fi).getSelectBody();
            handleSelectBody(sb, result, null);
            return null;
        } else if (fi instanceof SubJoin) {
            SubJoin sj = (SubJoin) fi;
            String left = registerFromItem(sj.getLeft(), result);
            List<Join> joins = sj.getJoinList();
            if (joins != null) {
                String prev = left;
                for (Join j : joins) {
                    String right = registerFromItem(j.getRightItem(), result);
                    if (right != null && prev != null) {
                        result.relations.add(new ExtractResult.ExtractRelation(prev, right, joinTypeOf(j)));
                    }
                    if (right != null) prev = right;
                }
            }
            return left;
        }
        return null;
    }

    private String registerTable(Table t, ExtractResult result) {
        String full = t.getName(); // 可能 db.t_user
        String shortName = shortName(t);
        result.tables.add(shortName);
        Alias alias = t.getAlias();
        if (alias != null && alias.getName() != null) {
            aliasToTable.put(alias.getName(), shortName);
        } else {
            aliasToTable.put(shortName, shortName);
        }
        return shortName;
    }

    /**
     * 解析 ON 条件，返回「左表」短名（即与 rightTable 不相同的那一侧所属表）。
     * pairs 收集等值连接字段对（已对齐：leftField 属左表，rightField 属右表）。
     */
    private String parseOnCondition(Expression on, String rightTable, String prevTable,
                                    List<ExtractResult.OnFieldPair> pairs) {
        if (on == null) return prevTable;
        List<Expression> equals = new ArrayList<>();
        flattenAnd(on, equals);
        String determinedLeft = null;
        for (Expression eq : equals) {
            if (!(eq instanceof EqualsTo)) continue;
            EqualsTo et = (EqualsTo) eq;
            String[] parsed = resolveColumnPair(et.getLeftExpression(), et.getRightExpression(), rightTable);
            if (parsed == null) continue;
            String leftTable = parsed[0];
            String leftField = parsed[1];
            String rightField = parsed[2];
            pairs.add(new ExtractResult.OnFieldPair(leftField, rightField));
            if (determinedLeft == null) {
                determinedLeft = leftTable;
            }
        }
        return determinedLeft != null ? determinedLeft : prevTable;
    }

    private void flattenAnd(Expression expr, List<Expression> out) {
        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            flattenAnd(and.getLeftExpression(), out);
            flattenAnd(and.getRightExpression(), out);
        } else {
            out.add(expr);
        }
    }

    /**
     * 给定等号两侧表达式，返回 [leftTable, leftField, rightField]；
     * 其中 rightField 属于 rightTable，leftField 属另一侧表。
     */
    private String[] resolveColumnPair(Expression a, Expression b, String rightTable) {
        String[] colA = columnInfo(a);
        String[] colB = columnInfo(b);
        if (colA == null || colB == null) return null;
        // colA/colB = [tableOrAlias, field]
        String tableA = resolveAlias(colA[0]);
        String tableB = resolveAlias(colB[0]);
        if (rightTable != null && tableB != null && tableB.equalsIgnoreCase(rightTable)) {
            return new String[]{tableA, colA[1], colB[1]};
        }
        if (rightTable != null && tableA != null && tableA.equalsIgnoreCase(rightTable)) {
            return new String[]{tableB, colB[1], colA[1]};
        }
        // 无法确定哪侧是 right，返回按 a 在左
        return new String[]{tableA != null ? tableA : (tableB != null ? tableB : null), colA[1], colB[1]};
    }

    /** 返回 [tableOrAlias, field]，非 Column 返回 null */
    private String[] columnInfo(Expression e) {
        if (e instanceof Column) {
            Column c = (Column) e;
            String tbl = c.getTable() != null ? c.getTable().getName() : null;
            String field = c.getColumnName() != null ? c.getColumnName() : "";
            return new String[]{tbl, field};
        }
        return null;
    }

    private String resolveAlias(String tableOrAlias) {
        if (tableOrAlias == null) return null;
        String resolved = aliasToTable.get(tableOrAlias);
        return resolved != null ? resolved : shortName(tableOrAlias);
    }

    private String joinTypeOf(Join join) {
        if (join.isCross()) return "CROSS";
        if (join.isFull()) return "FULL";
        if (join.isLeft()) return "LEFT";
        if (join.isRight()) return "RIGHT";
        if (join.isInner()) return "INNER";
        // JSqlParser: plain "JOIN" 默认 INNER
        return "INNER";
    }

    /** 去掉 schema 前缀，取最后一段并小写归一。 */
    static String shortName(String full) {
        if (full == null) return null;
        String s = full;
        int dot = s.lastIndexOf('.');
        if (dot >= 0) s = s.substring(dot + 1);
        return s.toLowerCase();
    }

    private String shortName(Table t) {
        return shortName(t.getName());
    }

    // ---- Druid 兜底：多方言解析失败时提取表名 ----
    private boolean druidFallback(String sql, ExtractResult result) {
        try {
            List<SQLStatement> stmts = SQLUtils.parseStatements(sql, DRUID_DB);
            if (stmts == null || stmts.isEmpty()) {
                return false;
            }
            SchemaStatVisitor visitor = SQLUtils.createSchemaStatVisitor(DRUID_DBTYPE);
            for (SQLStatement s : stmts) {
                s.accept(visitor);
            }
            boolean found = false;
            for (Object key : visitor.getTables().keySet()) {
                String tbl = key.toString();
                // key 形如 TableStat.Name，toString 通常是表名；保险起见取最后一段
                String sn = shortName(tbl);
                if (sn != null && !sn.isEmpty()) {
                    result.tables.add(sn);
                    found = true;
                }
            }
            return found;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 正则兜底 ----
    private void regexFallback(String sql, ExtractResult result) {
        Matcher m = FROM_JOIN_TABLE.matcher(sql);
        while (m.find()) {
            result.tables.add(shortName(m.group(1)));
        }
    }
}
