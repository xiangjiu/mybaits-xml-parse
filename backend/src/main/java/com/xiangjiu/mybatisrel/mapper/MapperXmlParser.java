package com.xiangjiu.mybatisrel.mapper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析单个 mapper.xml，返回其中的语句列表。
 * 用 JDK 自带 DOM 解析，不依赖 MyBatis 运行时。
 */
public class MapperXmlParser {

    private final DocumentBuilder docBuilder;
    private final Transformer transformer;

    public MapperXmlParser() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            // 禁用外部实体，安全
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            this.docBuilder = dbf.newDocumentBuilder();

            TransformerFactory tf = TransformerFactory.newInstance();
            this.transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
        } catch (Exception e) {
            throw new IllegalStateException("初始化 XML 解析器失败", e);
        }
    }

    /**
     * 解析一个文件。若不是 mapper.xml（根不是 mapper），返回空结果。
     */
    public ParsedMapperFile parse(File file) {
        ParsedMapperFile out = new ParsedMapperFile();
        Document doc;
        try {
            doc = docBuilder.parse(file);
        } catch (Exception e) {
            // 解析失败的文件跳过，不中断整体
            return out;
        }
        Element root = doc.getDocumentElement();
        if (root == null || !"mapper".equalsIgnoreCase(root.getTagName())) {
            return out;
        }
        String namespace = root.getAttribute("namespace");
        if (namespace == null || namespace.isEmpty()) {
            return out;
        }

        // 收集 <sql id> 片段
        Map<String, Node> sqlFragments = new LinkedHashMap<>();
        NodeList sqlNodes = root.getElementsByTagName("sql");
        for (int i = 0; i < sqlNodes.getLength(); i++) {
            Element el = (Element) sqlNodes.item(i);
            String id = el.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                sqlFragments.put(id, el);
            }
        }

        DynamicSqlFlattener flattener = new DynamicSqlFlattener(sqlFragments);

        String[] stmtTags = {"select", "insert", "update", "delete"};
        for (String tag : stmtTags) {
            NodeList nodes = root.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String id = el.getAttribute("id");
                if (id == null || id.isEmpty()) {
                    continue;
                }
                MapperStatement ms = new MapperStatement();
                ms.setNamespace(namespace);
                ms.setStatementId(id);
                ms.setType(tag);
                ms.setResultMap(nullIfEmpty(el.getAttribute("resultMap")));
                ms.setResultType(nullIfEmpty(el.getAttribute("resultType")));
                ms.setFileName(file.getName());
                ms.setRawXml(innerXml(el));
                ms.setFlatSql(flattener.flatten(el));
                out.statements.add(ms);
            }
        }

        // 解析 <resultMap> 的 association / collection（用于推断弱关联）
        NodeList rmNodes = root.getElementsByTagName("resultMap");
        for (int i = 0; i < rmNodes.getLength(); i++) {
            Element el = (Element) rmNodes.item(i);
            String id = el.getAttribute("id");
            if (id == null || id.isEmpty()) {
                continue;
            }
            ResultMapInfo rm = new ResultMapInfo();
            rm.namespace = namespace;
            rm.id = id;
            rm.fqn = namespace + "." + id;
            collectAssociations(el, rm.associations, "association", namespace);
            collectAssociations(el, rm.associations, "collection", namespace);
            out.resultMaps.add(rm);
        }

        return out;
    }

    /** 收集某个标签名（association/collection）的直接与嵌套节点。 */
    private void collectAssociations(Element parent, List<Association> sink, String tag, String namespace) {
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            Association a = new Association();
            a.kind = tag;
            a.property = nullIfEmpty(el.getAttribute("property"));
            a.column = nullIfEmpty(el.getAttribute("column"));
            a.select = nullIfEmpty(el.getAttribute("select"));
            a.resultMapRef = nullIfEmpty(el.getAttribute("resultMap"));
            a.javaType = nullIfEmpty(el.getAttribute("javaType"));
            if (a.javaType == null) {
                a.javaType = nullIfEmpty(el.getAttribute("ofType"));
            }
            sink.add(a);
        }
    }

    private String innerXml(Element el) {
        try {
            StringWriter sw = new StringWriter();
            NodeList children = el.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                transformer.transform(new DOMSource(child), new StreamResult(sw));
            }
            return sw.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
