package com.recruitment.controller;

import com.recruitment.dto.ApiResponse;
import com.recruitment.dto.ApplicationRequest;
import com.recruitment.model.Application;
import com.recruitment.service.AiService;
import com.recruitment.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/applications")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final AiService aiService;

    @PostMapping
    public ResponseEntity<ApiResponse<Application>> createApplication(
            @PathVariable Long orgId,
            @Valid @RequestBody ApplicationRequest request) {
        Application application = applicationService.createApplication(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Application created successfully", application));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Application>>> getApplicationsByOrg(@PathVariable Long orgId) {
        List<Application> applications = applicationService.getApplicationsByOrg(orgId);
        return ResponseEntity.ok(ApiResponse.success("Applications retrieved successfully", applications));
    }

    @GetMapping("/{appId}")
    public ResponseEntity<ApiResponse<Application>> getApplicationById(@PathVariable Long appId) {
        Application application = applicationService.getApplicationById(appId);
        return ResponseEntity.ok(ApiResponse.success("Application retrieved successfully", application));
    }

    @PutMapping("/{appId}/status")
    public ResponseEntity<ApiResponse<Application>> updateApplicationStatus(
            @PathVariable Long appId,
            @RequestBody Map<String, String> body) {
        Application application = applicationService.updateApplicationStatus(appId, body.get("status"));
        return ResponseEntity.ok(ApiResponse.success("Application status updated successfully", application));
    }

    @PostMapping("/{appId}/analyze")
    public ResponseEntity<ApiResponse<Application>> analyzeApplication(@PathVariable Long appId) {
        AiService.AiAnalysisResponse analysis = aiService.analyzeApplication(appId);
        Application application = applicationService.getApplicationById(appId);
        application.setAiScore(analysis.getScore());
        application.setAiAnalysis(analysis.getAnalysis());
        application = applicationService.updateApplicationStatus(appId, application.getStatus().name());
        return ResponseEntity.ok(ApiResponse.success("Application analyzed successfully", application));
    }
}
