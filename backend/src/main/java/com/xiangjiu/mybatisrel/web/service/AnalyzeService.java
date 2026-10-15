package com.xiangjiu.mybatisrel.web.service;

import com.xiangjiu.mybatisrel.mapper.XmlScanner;
import com.xiangjiu.mybatisrel.relation.AnalyzeResult;
import com.xiangjiu.mybatisrel.relation.RelationGraphBuilder;
import com.xiangjiu.mybatisrel.web.dto.ProgressResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 分析任务执行器：扫描目录 → 构建关系图 → 存入任务管理器。
 */
@Service
public class AnalyzeService {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeService.class);

    private final AnalyzeTaskManager taskManager;
    private final XmlScanner scanner = new XmlScanner();
    private final RelationGraphBuilder graphBuilder = new RelationGraphBuilder();

    @Autowired
    public AnalyzeService(AnalyzeTaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Async
    public void runAnalysis(String analyzeId, String baseDir) {
        ProgressResponse p = new ProgressResponse(ProgressResponse.RUNNING);
        taskManager.updateProgress(analyzeId, p);
        try {
            File dir = new File(baseDir);
            XmlScanner.ScanResult scan = scanner.scan(dir, new XmlScanner.ProgressListener() {
                @Override
                public void onTotal(int total) {
                    p.setTotal(total);
                }

                @Override
                public void onFile(int processed, int total, String fileName) {
                    p.setProcessed(processed);
                    p.setCurrentFile(fileName);
                }
            });
            log.info("analyze[{}] 扫描完成: files={}, mappers={}, statements={}",
                    analyzeId, scan.totalFiles, scan.mapperCount, scan.statements.size());

            AnalyzeResult result = graphBuilder.build(scan.statements, scan.resultMaps);
            taskManager.finish(analyzeId, result);
        } catch (Exception e) {
            log.error("analyze[{}] 失败", analyzeId, e);
            taskManager.fail(analyzeId, e.getMessage());
        }
    }
}
