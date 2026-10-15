package com.xiangjiu.mybatisrel.web.service;

import com.xiangjiu.mybatisrel.relation.AnalyzeResult;
import com.xiangjiu.mybatisrel.web.dto.ProgressResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 分析任务状态内存存储。
 * 简单起见用 ConcurrentHashMap；后续可换 Redis。
 */
@Component
public class AnalyzeTaskManager {

    private static class TaskState {
        volatile ProgressResponse progress = new ProgressResponse(ProgressResponse.QUEUED);
        volatile AnalyzeResult result;
        volatile long createdAt = System.currentTimeMillis();
    }

    private final ConcurrentHashMap<String, TaskState> tasks = new ConcurrentHashMap<>();

    public String create() {
        String id = newId();
        tasks.put(id, new TaskState());
        return id;
    }

    public ProgressResponse getProgress(String id) {
        TaskState s = tasks.get(id);
        return s == null ? null : s.progress;
    }

    public AnalyzeResult getResult(String id) {
        TaskState s = tasks.get(id);
        return s == null ? null : s.result;
    }

    public void updateProgress(String id, ProgressResponse p) {
        TaskState s = tasks.get(id);
        if (s != null) {
            s.progress = p;
        }
    }

    public void finish(String id, AnalyzeResult result) {
        TaskState s = tasks.get(id);
        if (s != null) {
            s.result = result;
            ProgressResponse p = s.progress;
            p.setStatus(ProgressResponse.DONE);
            p.setProcessed(p.getTotal());
        }
    }

    public void fail(String id, String message) {
        TaskState s = tasks.get(id);
        if (s != null) {
            s.progress.setStatus(ProgressResponse.FAILED);
            s.progress.setMessage(message);
        }
    }

    private String newId() {
        return Long.toHexString(System.nanoTime()) + Integer.toHexString((int) (Math.random() * 0xFFFF));
    }
}
