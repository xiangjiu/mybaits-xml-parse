package com.xiangjiu.mybatisrel.web.api;

import com.xiangjiu.mybatisrel.relation.AnalyzeResult;
import com.xiangjiu.mybatisrel.relation.TableEdge;
import com.xiangjiu.mybatisrel.relation.TableNode;
import com.xiangjiu.mybatisrel.web.dto.AnalyzeRequest;
import com.xiangjiu.mybatisrel.web.dto.ProgressResponse;
import com.xiangjiu.mybatisrel.web.dto.TableDetail;
import com.xiangjiu.mybatisrel.web.service.AnalyzeService;
import com.xiangjiu.mybatisrel.web.service.AnalyzeTaskManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyzeController {

    @Autowired
    private AnalyzeService analyzeService;
    @Autowired
    private AnalyzeTaskManager taskManager;

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody AnalyzeRequest req) {
        if (req.getBaseDir() == null || req.getBaseDir().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String id = taskManager.create();
        analyzeService.runAnalysis(id, req.getBaseDir());
        Map<String, Object> body = new HashMap<>();
        body.put("analyzeId", id);
        body.put("status", ProgressResponse.QUEUED);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/progress/{id}")
    public ResponseEntity<ProgressResponse> progress(@PathVariable String id) {
        ProgressResponse p = taskManager.getProgress(id);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(p);
    }

    @GetMapping("/result/{id}")
    public ResponseEntity<AnalyzeResult> result(@PathVariable String id) {
        AnalyzeResult r = taskManager.getResult(id);
        if (r == null) {
            // 可能还在运行或不存在
            ProgressResponse p = taskManager.getProgress(id);
            if (p != null && ProgressResponse.DONE.equals(p.getStatus())) {
                r = taskManager.getResult(id);
            }
            if (r == null) {
                return ResponseEntity.notFound().build();
            }
        }
        return ResponseEntity.ok(r);
    }

    @GetMapping("/table/{id}/{tableName}")
    public ResponseEntity<TableDetail> table(
            @PathVariable String id, @PathVariable String tableName) {
        AnalyzeResult r = taskManager.getResult(id);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        TableNode node = null;
        for (TableNode t : r.getTables()) {
            if (t.getName().equalsIgnoreCase(tableName)) {
                node = t;
                break;
            }
        }
        if (node == null) {
            return ResponseEntity.notFound().build();
        }
        TableDetail detail = new TableDetail();
        detail.setTable(node);
        List<TableDetail.RelatedEdge> related = new ArrayList<>();
        // 收集该表参与的边与来源
        List<com.xiangjiu.mybatisrel.relation.SqlSource> allSources = new ArrayList<>();
        for (TableEdge e : r.getEdges()) {
            boolean involves = e.getLeft().equalsIgnoreCase(node.getName())
                    || e.getRight().equalsIgnoreCase(node.getName());
            if (!involves) continue;
            String other = e.getLeft().equalsIgnoreCase(node.getName()) ? e.getRight() : e.getLeft();
            related.add(new TableDetail.RelatedEdge(
                    other, e.getJoinType(), e.getOnFieldsList(), e.getStrength()));
            allSources.addAll(e.getSources());
        }
        detail.getRelatedEdges().addAll(related);
        detail.getSqlSources().addAll(allSources);
        return ResponseEntity.ok(detail);
    }

    @GetMapping(value = "/export/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> export(@PathVariable String id,
                                          @RequestParam(defaultValue = "mermaid") String format) {
        AnalyzeResult r = taskManager.getResult(id);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        if ("mermaid".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(toMermaid(r));
        }
        return ResponseEntity.badRequest().body("unsupported format: " + format);
    }

    private String toMermaid(AnalyzeResult r) {
        StringBuilder sb = new StringBuilder("erDiagram\n");
        // 表定义
        for (TableNode t : r.getTables()) {
            sb.append("  ").append(t.getName()).append(" {\n");
            if (t.getFields().isEmpty()) {
                sb.append("    string id\n");
            } else {
                for (String f : t.getFields()) {
                    sb.append("    string ").append(f).append("\n");
                }
            }
            sb.append("  }\n");
        }
        // 关系
        for (TableEdge e : r.getEdges()) {
            String rel = "INNER".equals(e.getJoinType()) ? "||--||" : "}o--o{";
            String label = e.getJoinType();
            if (!e.getOnFields().isEmpty()) {
                com.xiangjiu.mybatisrel.relation.OnField f0 =
                        e.getOnFields().iterator().next();
                label = e.getJoinType() + " " + f0.getLeftField() + "=" + f0.getRightField();
            }
            sb.append("  ").append(e.getLeft()).append(" ")
              .append(rel).append(" ")
              .append(e.getRight()).append(" : \"")
              .append(label).append("\"\n");
        }
        return sb.toString();
    }
}
