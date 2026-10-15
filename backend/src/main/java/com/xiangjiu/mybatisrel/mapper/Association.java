package com.xiangjiu.mybatisrel.mapper;

import java.util.ArrayList;
import java.util.List;

/**
 * resultMap 中的 association / collection 节点。
 * 用于推断「弱关联」：若它通过 select 指向另一条语句，或通过 resultMap 指向
 * 另一个被某语句使用的 resultMap，则两端表存在隐式关联。
 */
public class Association {

    public String property;
    /** 父表中的外键列（column 属性） */
    public String column;
    /** 指向另一条语句：namespace.id（通常全限定） */
    public String select;
    /** 指向另一个 resultMap：id（可能局部或全限定） */
    public String resultMapRef;
    /** javaType / ofType 简单类名（仅记录，无法直接映射表时用不上） */
    public String javaType;
    /** association / collection */
    public String kind;

    public List<Association> children = new ArrayList<>();
}
