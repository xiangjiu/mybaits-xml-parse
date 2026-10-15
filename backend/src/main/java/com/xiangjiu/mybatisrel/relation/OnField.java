package com.xiangjiu.mybatisrel.relation;

/**
 * JOIN 等值连接中的一个字段对：left.col = right.col
 */
public class OnField {

    private String leftField;
    private String rightField;

    public OnField() {}

    public OnField(String leftField, String rightField) {
        this.leftField = leftField;
        this.rightField = rightField;
    }

    public String getLeftField() { return leftField; }
    public void setLeftField(String leftField) { this.leftField = leftField; }
    public String getRightField() { return rightField; }
    public void setRightField(String rightField) { this.rightField = rightField; }
}
