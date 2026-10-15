package com.xiangjiu.mybatisrel.mapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个 resultMap 的解析结果。
 */
public class ResultMapInfo {

    public String namespace;
    public String id;
    /** 全限定 id：namespace.id */
    public String fqn;
    public final List<Association> associations = new ArrayList<>();
}
