package com.recruitment.controller;

import com.recruitment.model.*;
import com.recruitment.repository.*;
import com.recruitment.service.ResumeParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/organizations/{orgId}/ai-batch")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AiBatchController {

    private static final int MAX_RESUMES = 5;

    private final ResumeParserService resumeParserService;
    private final OrganizationRepository organizationRepository;
    private final JobPostRepository jobPostRepository;
    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final WebClient webClient;

    @PostMapping("/setup")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'HR', 'RECRUITER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> setupBatch(
            @PathVariable Long orgId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("jobDescription") String jobDescription,
            @RequestParam("role") String role,
            @RequestParam("round") String round) {

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "At least one resume is required"));
        }
        if (files.length > MAX_RESUMES) {
            return ResponseEntity.badRequest().body(Map.of("error", "Maximum " + MAX_RESUMES + " resumes allowed"));
        }
        if (jobDescription == null || jobDescription.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Job description is required"));
        }

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found with id: " + orgId));

        JobPost job = JobPost.builder()
                .organization(org)
                .title(role == null || role.isBlank() ? "Hiring Role" : role)
                .description(jobDescription)
                .company(org.getName())
                .interviewRound(round)
                .employmentType(JobPost.EmploymentType.FULL_TIME)
                .status(JobPost.JobStatus.ACTIVE)
                .build();
        job = jobPostRepository.save(job);

        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> candidatesForAi = new ArrayList<>();

        for (MultipartFile file : files) {
            ResumeParserService.ParsedResume parsed = resumeParserService.parse(file);
            String[] nameParts = parsed.getName().split("\\s+", 2);

            Candidate candidate = Candidate.builder()
                    .organization(org)
                    .firstName(nameParts[0])
                    .lastName(nameParts.length > 1 ? nameParts[1] : "")
                    .email(parsed.getEmail())
                    .phone(parsed.getPhone())
                    .resumeText(parsed.getRawText())
                    .skills(parsed.getSkills())
                    .experience(parsed.getExperience())
                    .build();
            candidate = candidateRepository.save(candidate);

            Application application = Application.builder()
                    .organization(org)
                    .jobPost(job)
                    .candidate(candidate)
                    .status(Application.ApplicationStatus.SUBMITTED)
                    .build();
            applicationRepository.save(application);

            Map<String, Object> item = new HashMap<>();
            item.put("fileName", file.getOriginalFilename());
            item.put("candidateId", candidate.getId());
            item.put("name", parsed.getName());
            item.put("email", parsed.getEmail());
            item.put("experience", parsed.getExperience());
            item.put("skills", parsed.getSkills());
            results.add(item);

            Map<String, Object> aiCandidate = new HashMap<>();
            aiCandidate.put("candidate_id", String.valueOf(candidate.getId()));
            aiCandidate.put("name", parsed.getName());
            aiCandidate.put("email", parsed.getEmail());
            aiCandidate.put("resume_text", parsed.getRawText());
            candidatesForAi.add(aiCandidate);

            log.info("Batch resume parsed: {} -> candidate {} ({})",
                    file.getOriginalFilename(), candidate.getId(), parsed.getEmail());
        }

        String batchId = job.getId() + "-" + System.currentTimeMillis();
        Map<String, Object> aiResult = null;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("batch_id", batchId);
            body.put("job_description", jobDescription);
            body.put("role", role == null ? "" : role);
            body.put("round", round == null ? "" : round);
            body.put("candidates", candidatesForAi);

            aiResult = webClient.post()
                    .uri("/api/ai/interview/index-batch")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            log.info("Vector index result for batch {}: {}", batchId, aiResult);
        } catch (Exception e) {
            log.warn("Failed to index batch {} into vector DB (non-fatal): {}", batchId, e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ready");
        response.put("jobId", job.getId());
        response.put("batchId", batchId);
        response.put("role", role);
        response.put("round", round);
        response.put("vectorIndexed", aiResult != null);
        response.put("candidates", results);

        return ResponseEntity.ok(response);
    }
}
