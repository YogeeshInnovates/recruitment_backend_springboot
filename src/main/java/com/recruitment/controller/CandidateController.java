package com.recruitment.controller;

import com.recruitment.dto.ApiResponse;
import com.recruitment.dto.CandidateRequest;
import com.recruitment.model.Candidate;
import com.recruitment.service.CandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/organizations/{orgId}/candidates")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    @PostMapping
    public ResponseEntity<ApiResponse<Candidate>> createCandidate(
            @PathVariable Long orgId,
            @Valid @RequestBody CandidateRequest request) {
        Candidate candidate = candidateService.createCandidate(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Candidate created successfully", candidate));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Candidate>>> getCandidatesByOrg(@PathVariable Long orgId) {
        List<Candidate> candidates = candidateService.getCandidatesByOrg(orgId);
        return ResponseEntity.ok(ApiResponse.success("Candidates retrieved successfully", candidates));
    }

    @GetMapping("/{candidateId}")
    public ResponseEntity<ApiResponse<Candidate>> getCandidateById(@PathVariable Long candidateId) {
        Candidate candidate = candidateService.getCandidateById(candidateId);
        return ResponseEntity.ok(ApiResponse.success("Candidate retrieved successfully", candidate));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<List<Candidate>>> uploadResumes(
            @PathVariable Long orgId,
            @RequestParam("files") List<MultipartFile> files) {
        List<Candidate> candidates = candidateService.uploadResumes(orgId, files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resumes uploaded successfully. " + candidates.size() + " candidate(s) created.", candidates));
    }

    @PutMapping("/{candidateId}")
    public ResponseEntity<ApiResponse<Candidate>> updateCandidate(
            @PathVariable Long candidateId,
            @Valid @RequestBody CandidateRequest request) {
        Candidate candidate = candidateService.updateCandidate(candidateId, request);
        return ResponseEntity.ok(ApiResponse.success("Candidate updated successfully", candidate));
    }

    @DeleteMapping("/{candidateId}")
    public ResponseEntity<ApiResponse<Void>> deleteCandidate(@PathVariable Long candidateId) {
        candidateService.deleteCandidate(candidateId);
        return ResponseEntity.ok(ApiResponse.success("Candidate deleted successfully"));
    }
}
