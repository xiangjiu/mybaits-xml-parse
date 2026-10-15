package com.xiangjiu.mybatisrel.mapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 递归扫描目录，找到所有 mapper.xml 文件。
 */
public class XmlScanner {

    private final MapperXmlParser parser = new MapperXmlParser();

    /**
     * 扫描 baseDir 下所有疑似 mapper.xml 的文件并解析。
     */
    public ScanResult scan(File baseDir) {
        return scan(baseDir, null);
    }

    /**
     * 带逐文件进度回调的扫描。
     */
    public ScanResult scan(File baseDir, ProgressListener listener) {
        ScanResult result = new ScanResult();
        if (baseDir == null || !baseDir.exists()) {
            return result;
        }
        List<File> files = new ArrayList<>();
        collect(baseDir, files);
        result.totalFiles = files.size();
        if (listener != null) {
            listener.onTotal(result.totalFiles);
        }
        int processed = 0;
        for (File f : files) {
            if (listener != null) {
                listener.onFile(processed, result.totalFiles, f.getName());
            }
            List<MapperStatement> stmts = parser.parse(f);
            if (!stmts.isEmpty()) {
                result.mapperCount++;
            }
            for (MapperStatement ms : stmts) {
                if (ms.getFlatSql() == null || ms.getFlatSql().isEmpty()) {
                    result.parseFailCount++;
                }
            }
            result.statements.addAll(stmts);
            processed++;
        }
        if (listener != null) {
            listener.onFile(processed, result.totalFiles, "");
        }
        return result;
    }

    /** 进度回调接口。 */
    public interface ProgressListener {
        void onTotal(int total);
        void onFile(int processed, int total, String fileName);
    }

    private void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        // 保证顺序稳定
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : children) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (isMapperXml(f.getName())) {
                out.add(f);
            }
        }
    }

    private boolean isMapperXml(String name) {
        // 接受所有 .xml，是否为 mapper 由 parser 看根节点决定
        return name != null && name.toLowerCase().endsWith(".xml");
    }

    public static class ScanResult {
        public int totalFiles;
        public int mapperCount;
        public int parseFailCount;
        public final List<MapperStatement> statements = new ArrayList<>();
    }
}
