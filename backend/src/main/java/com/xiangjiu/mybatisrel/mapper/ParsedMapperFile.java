package com.xiangjiu.mybatisrel.mapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个 mapper.xml 文件的解析结果：语句 + resultMap。
 */
public class ParsedMapperFile {

    public final List<MapperStatement> statements = new ArrayList<>();
    public final List<ResultMapInfo> resultMaps = new ArrayList<>();
}
