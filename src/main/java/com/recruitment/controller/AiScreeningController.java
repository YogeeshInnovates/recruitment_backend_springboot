package com.recruitment.controller;

import com.recruitment.service.AiScreeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/ai-screening")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AiScreeningController {

    private final AiScreeningService aiScreeningService;

    @PostMapping("/screen")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'HR', 'RECRUITER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> screen(
            @PathVariable Long orgId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("jobDescription") String jobDescription,
            @RequestParam("role") String role,
            @RequestParam("round") String round,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = "Referer", required = false) String referer) {
        try {
            Map<String, Object> response = aiScreeningService.screenBatch(
                    orgId, files, jobDescription, role, round,
                    origin != null && !origin.isBlank() ? origin : referer);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("AI screening failed for org {}: {}", orgId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "AI screening failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/status/{batchId}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'HR', 'RECRUITER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long orgId,
                                                      @PathVariable String batchId) {
        try {
            return ResponseEntity.ok(aiScreeningService.getBatchStatus(batchId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch screening status {}: {}", batchId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch status"));
        }
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'HR', 'RECRUITER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> pending(@PathVariable Long orgId) {
        long pending = aiScreeningService.pendingInterviews(orgId);
        return ResponseEntity.ok(Map.of("pending", pending));
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'HR', 'RECRUITER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> confirm(@PathVariable Long orgId,
                                                       @RequestBody Map<String, Object> body) {
        Object batchIdValue = body.get("batchId");
        if (batchIdValue == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "batchId is required"));
        }
        String batchId = String.valueOf(batchIdValue).trim();
        try {
            return ResponseEntity.ok(aiScreeningService.confirmBatch(batchId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Screening confirm failed for batch {}: {}", batchId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to confirm screening: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}