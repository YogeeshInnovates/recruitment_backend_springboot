package com.recruitment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.model.MalpracticeReport;
import com.recruitment.service.MalpracticeReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class MalpracticeController {

    private final MalpracticeReportService reportService;
    private final ObjectMapper objectMapper;

    @GetMapping("/{interviewId}/malpractice-report")
    public ResponseEntity<Map<String, Object>> getReport(@PathVariable Long interviewId) {
        MalpracticeReport r = reportService.getOrGenerate(interviewId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("interviewId", r.getInterviewId());
        body.put("severity", r.getSeverity());
        body.put("suspiciousEventCount", r.getSuspiciousEventCount());
        body.put("evidenceCount", r.getEvidenceCount());
        body.put("summary", r.getSummary());
        body.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        try {
            body.put("eventBreakdown", r.getEventBreakdown() == null ? new HashMap<String, Object>()
                    : objectMapper.readValue(r.getEventBreakdown(), Map.class));
        } catch (Exception e) {
            body.put("eventBreakdown", new HashMap<String, Object>());
        }
        return ResponseEntity.ok(body);
    }
}
