package com.xiangjiu.mybatisrel.relation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 表关联边：left 表与 right 表的关联。
 * 多次 JOIN 同一对表会被合并到同一条边。
 */
public class TableEdge {

    private String left;
    private String right;
    /** JOIN 类型：INNER / LEFT / RIGHT / FULL / CROSS / SUBQUERY 等 */
    private String joinType;
    /** 等值连接字段对 */
    private Set<OnField> onFields = new LinkedHashSet<>();
    /** 关联出现的来源 SQL */
    private List<SqlSource> sources = new ArrayList<>();
    /** STRONG = 显式 JOIN；WEAK = resultMap association/collection 暗示 */
    private String strength;

    public TableEdge() {}

    public TableEdge(String left, String right, String joinType, String strength) {
        this.left = left;
        this.right = right;
        this.joinType = joinType;
        this.strength = strength;
    }

    /** 取更强的 JOIN 类型：显式 JOIN 覆盖弱关联；LEFT/RIGHT/FULL 优先于 INNER */
    public static String stronger(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.equals(b)) return a;
        // 任一为非 INNER 则取非 INNER
        if (!a.equals("INNER")) return a;
        if (!b.equals("INNER")) return b;
        return a;
    }

    public void addOnField(OnField f) {
        if (f != null) {
            onFields.add(f);
        }
    }

    public void addSource(SqlSource s) {
        if (s != null) {
            sources.add(s);
        }
    }

    public String getEdgeKey() {
        return left + "|" + right;
    }

    public String getName() {
        return left + "→" + right;
    }

    public String getLeft() { return left; }
    public void setLeft(String left) { this.left = left; }
    public String getRight() { return right; }
    public void setRight(String right) { this.right = right; }
    public String getJoinType() { return joinType; }
    public void setJoinType(String joinType) { this.joinType = joinType; }
    public List<OnField> getOnFieldsList() { return new ArrayList<>(onFields); }
    public Set<OnField> getOnFields() { return onFields; }
    public List<SqlSource> getSources() { return sources; }
    public String getStrength() { return strength; }
    public void setStrength(String strength) { this.strength = strength; }
}
