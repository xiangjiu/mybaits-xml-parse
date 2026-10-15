package com.xiangjiu.mybatisrel.mapper;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 MyBatis 动态 SQL 节点树展平成一条「代表性」SQL 字符串。
 *
 * <p>策略（贪心，目的是拼出一条可被 SQL 解析器解析的结构性 SQL，不追求业务语义 100% 准确）：
 * <ul>
 *   <li>{@code <if>}: 取内容（视为 true）</li>
 *   <li>{@code <where>}/{@code <trim prefix="WHERE" ...>}: 取内容，去首尾的 AND/OR/逗号</li>
 *   <li>{@code <set>}: 取内容，去尾部逗号</li>
 *   <li>{@code <choose>}: 取第一个 {@code <when>}（无则 {@code <otherwise>})</li>
 *   <li>{@code <foreach>}: 展开为单个占位项（如 {@code IN (?)})</li>
 *   <li>{@code <include refid>}: 用 {@code <sql>} 片段替换</li>
 *   <li>{@code #{x}} / {@code ${x}}: 替换为 {@code ?}</li>
 * </ul>
 */
public class DynamicSqlFlattener {

    private final Map<String, Node> sqlFragments;

    public DynamicSqlFlattener(Map<String, Node> sqlFragments) {
        this.sqlFragments = sqlFragments == null ? new LinkedHashMap<>() : sqlFragments;
    }

    /**
     * 展平一个语句元素的内部内容。
     */
    public String flatten(Node statementNode) {
        StringBuilder sb = new StringBuilder();
        appendChildren(statementNode, sb);
        return normalize(sb.toString());
    }

    private void appendNode(Node node, StringBuilder sb) {
        if (node == null) {
            return;
        }
        short type = node.getNodeType();
        if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
            sb.append(node.getNodeValue());
            return;
        }
        if (type != Node.ELEMENT_NODE) {
            return;
        }
        String name = node.getNodeName().toLowerCase();
        switch (name) {
            case "if":
                // 贪心：视为 true，取子内容
                appendChildren(node, sb);
                break;
            case "where":
                // 取子内容，去掉首尾 AND/OR
                String w = innerString(node);
                sb.append(" WHERE ").append(stripAffixes(w, new String[]{"AND", "OR"}));
                break;
            case "set":
                String s = innerString(node);
                sb.append(" SET ").append(stripTrailingComma(s));
                break;
            case "trim": {
                String prefix = attr(node, "prefix");
                String body = innerString(node);
                String suffix = attr(node, "suffix");
                String prefixOverrides = attr(node, "prefixOverrides");
                String suffixOverrides = attr(node, "suffixOverrides");
                String trimmed = body;
                if (prefixOverrides != null) {
                    trimmed = stripLeading(trimmed, prefixOverrides);
                }
                if (suffixOverrides != null) {
                    trimmed = stripTrailing(trimmed, suffixOverrides);
                }
                if (prefix != null) sb.append(' ').append(prefix).append(' ');
                sb.append(trimmed);
                if (suffix != null) sb.append(' ').append(suffix);
                break;
            }
            case "choose": {
                Node when = firstChildByName(node, "when");
                if (when != null) {
                    appendChildren(when, sb);
                } else {
                    Node otherwise = firstChildByName(node, "otherwise");
                    if (otherwise != null) {
                        appendChildren(otherwise, sb);
                    }
                }
                break;
            }
            case "when":
            case "otherwise":
                appendChildren(node, sb);
                break;
            case "foreach": {
                // 展开为单个占位项：collection 通常是 list，用一次 ? 代表
                String open = attr(node, "open");
                String close = attr(node, "close");
                String item = attr(node, "item");
                if (open != null) sb.append(' ').append(open).append(' ');
                // foreach 体里通常用 #{item}，这里放一个占位
                sb.append('?');
                if (close != null) sb.append(' ').append(close);
                break;
            }
            case "include": {
                String refid = attr(node, "refid");
                if (refid != null) {
                    Node frag = sqlFragments.get(refid);
                    if (frag != null) {
                        appendChildren(frag, sb);
                    }
                }
                break;
            }
            case "bind":
            case "sql":
                // bind: 跳过；sql: 已作为片段收集，不在语句体里展开
                break;
            default:
                // 未知标签：保守地取子文本（可能是 <association> 等非 SQL 节点，不会出现在语句体内）
                appendChildren(node, sb);
                break;
        }
    }

    private void appendChildren(Node node, StringBuilder sb) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            appendNode(children.item(i), sb);
        }
    }

    private String innerString(Node node) {
        StringBuilder sb = new StringBuilder();
        appendChildren(node, sb);
        return sb.toString();
    }

    private String attr(Node node, String name) {
        Node attr = node.getAttributes().getNamedItem(name);
        return attr == null ? null : attr.getNodeValue();
    }

    private Node firstChildByName(Node parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE
                    && name.equalsIgnoreCase(c.getNodeName())) {
                return c;
            }
        }
        return null;
    }

    /** 去掉字符串首尾指定的连接词（AND/OR 等），忽略大小写与前后空白。 */
    private String stripAffixes(String s, String[] words) {
        String trimmed = s.trim();
        // 去前缀
        for (String w : words) {
            String ww = w.trim();
            if (!ww.isEmpty()
                    && trimmed.length() >= ww.length()
                    && trimmed.substring(0, ww.length()).equalsIgnoreCase(ww)) {
                trimmed = trimmed.substring(ww.length()).trim();
                break;
            }
        }
        // 去后缀
        for (String w : words) {
            String ww = w.trim();
            if (!ww.isEmpty()
                    && trimmed.length() >= ww.length()
                    && trimmed.substring(trimmed.length() - ww.length()).equalsIgnoreCase(ww)) {
                trimmed = trimmed.substring(0, trimmed.length() - ww.length()).trim();
                break;
            }
        }
        return trimmed;
    }

    private String stripLeading(String s, String overrides) {
        // overrides 形如 "AND |OR "，多个用 | 分隔
        String[] parts = overrides.split("\\|");
        String trimmed = s.trim();
        for (String p : parts) {
            String pp = p.trim();
            if (!pp.isEmpty()
                    && trimmed.length() >= pp.length()
                    && trimmed.substring(0, pp.length()).equalsIgnoreCase(pp)) {
                trimmed = trimmed.substring(pp.length()).trim();
                break;
            }
        }
        return trimmed;
    }

    private String stripTrailing(String s, String overrides) {
        String[] parts = overrides.split("\\|");
        String trimmed = s.trim();
        for (String p : parts) {
            String pp = p.trim();
            if (!pp.isEmpty()) {
                if (trimmed.toLowerCase().endsWith(pp.toLowerCase())) {
                    trimmed = trimmed.substring(0, trimmed.length() - pp.length()).trim();
                }
            }
        }
        return trimmed;
    }

    private String stripTrailingComma(String s) {
        String trimmed = s.trim();
        while (trimmed.endsWith(",")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /** 占位符归一化 + 压缩空白。 */
    private String normalize(String s) {
        if (s == null) return "";
        // #{xxx} 和 ${xxx} → ?
        String r = s.replaceAll("#\\{[^}]*}", "?");
        r = r.replaceAll("\\$\\{[^}]*}", "?");
        // 压缩连续空白
        r = r.replaceAll("\\s+", " ").trim();
        return r;
    }
}
