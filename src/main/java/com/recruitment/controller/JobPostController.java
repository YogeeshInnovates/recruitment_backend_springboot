package com.recruitment.controller;

import com.recruitment.dto.ApiResponse;
import com.recruitment.dto.JobPostRequest;
import com.recruitment.model.JobPost;
import com.recruitment.service.JobPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations/{orgId}/jobs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class JobPostController {

    private final JobPostService jobPostService;

    @PostMapping
    public ResponseEntity<ApiResponse<JobPost>> createJob(
            @PathVariable Long orgId,
            @Valid @RequestBody JobPostRequest request) {
        JobPost job = jobPostService.createJob(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Job post created successfully", job));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobPost>>> getJobsByOrg(@PathVariable Long orgId) {
        List<JobPost> jobs = jobPostService.getJobsByOrg(orgId);
        return ResponseEntity.ok(ApiResponse.success("Jobs retrieved successfully", jobs));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobPost>> getJobById(@PathVariable Long jobId) {
        JobPost job = jobPostService.getJobById(jobId);
        return ResponseEntity.ok(ApiResponse.success("Job retrieved successfully", job));
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobPost>> updateJob(
            @PathVariable Long jobId,
            @Valid @RequestBody JobPostRequest request) {
        JobPost job = jobPostService.updateJob(jobId, request);
        return ResponseEntity.ok(ApiResponse.success("Job updated successfully", job));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable Long jobId) {
        jobPostService.deleteJob(jobId);
        return ResponseEntity.ok(ApiResponse.success("Job deleted successfully"));
    }
}
