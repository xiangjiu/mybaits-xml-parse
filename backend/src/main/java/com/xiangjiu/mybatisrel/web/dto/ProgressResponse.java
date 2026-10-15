package com.xiangjiu.mybatisrel.web.dto;

public class ProgressResponse {

    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    private String status;
    private int processed;
    private int total;
    private String currentFile;
    private String message;

    public ProgressResponse() {}

    public ProgressResponse(String status) {
        this.status = status;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getProcessed() { return processed; }
    public void setProcessed(int processed) { this.processed = processed; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public String getCurrentFile() { return currentFile; }
    public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
