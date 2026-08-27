package com.recruitment.service;

import com.recruitment.model.*;
import com.recruitment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiScreeningService {

    public static final int MAX_RESUMES = 10;

    private final ResumeParserService resumeParserService;
    private final OrganizationRepository organizationRepository;
    private final JobPostRepository jobPostRepository;
    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final EmailService emailService;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key}")
    private String apiKey;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${app.timezone:Asia/Kolkata}")
    private String appTimezone;

    private final Map<String, Map<String, Object>> batches = new ConcurrentHashMap<>();

    private WebClient screeningWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(180))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        return WebClient.builder()
                .baseUrl(aiServiceUrl)
                .filter((request, next) -> {
                    ClientRequest filtered = ClientRequest.from(request)
                            .header("X-Internal-Api-Key", apiKey)
                            .build();
                    return next.exchange(filtered);
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Map<String, Object> screenBatch(Long orgId, MultipartFile[] files,
                                           String jobDescription, String role, String round,
                                           String requestOrigin) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("At least one resume is required");
        }
        if (files.length > MAX_RESUMES) {
            throw new IllegalArgumentException("Maximum " + MAX_RESUMES + " resumes allowed");
        }
        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("Job description is required");
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

        List<Map<String, Object>> candidatesForAi = new ArrayList<>();
        Map<String, Long> appIdByCandidateId = new HashMap<>();
        Map<String, String> fileNameByCandidateId = new HashMap<>();

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
            application = applicationRepository.save(application);
            appIdByCandidateId.put(String.valueOf(candidate.getId()), application.getId());

            Map<String, Object> aiCandidate = new HashMap<>();
            aiCandidate.put("candidate_id", String.valueOf(candidate.getId()));
            aiCandidate.put("name", parsed.getName());
            aiCandidate.put("email", parsed.getEmail());
            aiCandidate.put("resume_text", parsed.getRawText());
            candidatesForAi.add(aiCandidate);
            fileNameByCandidateId.put(String.valueOf(candidate.getId()), file.getOriginalFilename());

            log.info("Screening batch parsed: {} -> candidate {}", file.getOriginalFilename(), candidate.getId());
        }

        String batchId = job.getId() + "-" + System.currentTimeMillis();
        Map<String, Object> body = new HashMap<>();
        body.put("batch_id", batchId);
        body.put("job_description", jobDescription);
        body.put("role", role == null ? "" : role);
        body.put("round", round == null ? "" : round);
        body.put("candidates", candidatesForAi);

        Map<String, Object> screening;
        try {
            screening = screeningWebClient().post()
                    .uri("/api/ai/screening/run")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(170))
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("AI screening failed: " + e.getMessage());
        }
        if (screening == null) {
            throw new RuntimeException("AI screening returned no result");
        }

        String baseUrl = EmailService.resolveBaseUrl(frontendUrl, requestOrigin);
        List<Map<String, Object>> scoredCandidates = new ArrayList<>();
        for (Object obj : (List<?>) screening.getOrDefault("candidates", Collections.emptyList())) {
            Map<String, Object> item = new HashMap<>((Map<String, Object>) obj);
            String cid = String.valueOf(item.get("candidate_id"));
            Long appId = appIdByCandidateId.get(cid);
            if (appId != null) {
                item.put("applicationId", appId);
                item.put("candidateId", Long.parseLong(cid));
            }
            item.put("fileName", fileNameByCandidateId.getOrDefault(cid, ""));
            item.put("interviewUrl", baseUrl + "/interview/" + (appId != null ? cid : ""));
            scoredCandidates.add(item);
        }

        List<Map<String, Object>> schedule = (List<Map<String, Object>>) screening.getOrDefault("schedule", Collections.emptyList());

        Map<String, Object> batch = new ConcurrentHashMap<>();
        batch.put("batchId", batchId);
        batch.put("jobId", job.getId());
        batch.put("organizationId", orgId);
        batch.put("role", role);
        batch.put("round", round);
        batch.put("baseUrl", baseUrl);
        batch.put("screening", screening);
        batch.put("candidates", scoredCandidates);
        batch.put("schedule", schedule);
        batch.put("appIdByCandidateId", appIdByCandidateId);
        batch.put("createdAt", System.currentTimeMillis());
        batches.put(batchId, batch);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "screened");
        response.put("batchId", batchId);
        response.put("jobId", job.getId());
        response.put("role", role);
        response.put("round", round);
        response.put("source", screening.getOrDefault("source", "gemini"));
        response.put("candidates", scoredCandidates);
        response.put("schedule", schedule);
        return response;
    }

    public Map<String, Object> getBatchStatus(String batchId) {
        Map<String, Object> batch = batches.get(batchId);
        if (batch == null) {
            batch = rebuildBatch(batchId);
        }
        if (batch == null) {
            throw new IllegalArgumentException("No screening batch found for " + batchId);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("status", batch.get("confirmed") != null && Boolean.TRUE.equals(batch.get("confirmed")) ? "confirmed" : "screened");
        response.put("batchId", batchId);
        response.put("jobId", batch.get("jobId"));
        response.put("role", batch.get("role"));
        response.put("round", batch.get("round"));
        response.put("candidates", batch.get("candidates"));
        response.put("schedule", batch.get("schedule"));
        return response;
    }

    private Map<String, Object> rebuildBatch(String batchId) {
        try {
            Long jobId = Long.parseLong(batchId.split("-")[0]);
            JobPost job = jobPostRepository.findById(jobId).orElse(null);
            if (job == null) return null;

            Map<String, Object> screening;
            try {
                screening = screeningWebClient().get()
                        .uri("/api/ai/screening/result/{batchId}", batchId)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(30))
                        .block();
            } catch (Exception e) {
                log.warn("Failed to reload screening batch {} from AI service: {}", batchId, e.getMessage());
                return null;
            }
            if (screening == null) return null;

            String baseUrl = EmailService.resolveBaseUrl(frontendUrl, null);
            Map<String, Long> appIdByCandidateId = new HashMap<>();
            for (Application app : applicationRepository.findByJobPostId(jobId)) {
                appIdByCandidateId.put(String.valueOf(app.getCandidate().getId()), app.getId());
            }

            List<Map<String, Object>> scoredCandidates = new ArrayList<>();
            for (Object obj : (List<?>) screening.getOrDefault("candidates", Collections.emptyList())) {
                Map<String, Object> item = new HashMap<>((Map<String, Object>) obj);
                String cid = String.valueOf(item.get("candidate_id"));
                Long appId = appIdByCandidateId.get(cid);
                item.put("applicationId", appId);
                item.put("candidateId", cid);
                item.put("interviewUrl", baseUrl + "/interview/" + cid);
                scoredCandidates.add(item);
            }

            Map<String, Object> batch = new ConcurrentHashMap<>();
            batch.put("batchId", batchId);
            batch.put("jobId", jobId);
            batch.put("organizationId", job.getOrganization().getId());
            batch.put("role", screening.getOrDefault("role", ""));
            batch.put("round", screening.getOrDefault("round", ""));
            batch.put("baseUrl", baseUrl);
            batch.put("screening", screening);
            batch.put("candidates", scoredCandidates);
            batch.put("schedule", screening.getOrDefault("schedule", Collections.emptyList()));
            batch.put("appIdByCandidateId", appIdByCandidateId);
            batches.put(batchId, batch);
            return batch;
        } catch (Exception e) {
            log.warn("Failed to rebuild screening batch {}: {}", batchId, e.getMessage());
            return null;
        }
    }

    @Transactional
    public Map<String, Object> confirmBatch(String batchId) {
        Map<String, Object> batch = batches.get(batchId);
        if (batch == null) {
            batch = rebuildBatch(batchId);
        }
        if (batch == null) {
            throw new IllegalArgumentException("No screening batch found for " + batchId + ". Re-run screening first.");
        }
        if (Boolean.TRUE.equals(batch.get("confirmed"))) {
            Map<String, Object> already = new HashMap<>();
            already.put("status", "confirmed");
            already.put("batchId", batchId);
            already.put("interviewsScheduled", batch.get("interviewsScheduled"));
            return already;
        }

        // Mark confirmed on the AI service (persists batch-level state)
        try {
            Map<String, Object> confirmBody = new HashMap<>();
            confirmBody.put("batch_id", batchId);
            screeningWebClient().post()
                    .uri("/api/ai/screening/confirm")
                    .bodyValue(confirmBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (Exception e) {
            log.warn("Failed to confirm batch on AI service (non-fatal): {}", e.getMessage());
        }

        JobPost job = jobPostRepository.findById((Long) batch.get("jobId")).orElse(null);
        Map<String, Long> appIdByCandidateId = (Map<String, Long>) batch.get("appIdByCandidateId");
        String round = (String) batch.get("round");
        String baseUrl = (String) batch.get("baseUrl");
        List<Map<String, Object>> schedule = (List<Map<String, Object>>) batch.get("schedule");

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, Object> slot : schedule) {
            try {
                String cid = String.valueOf(slot.get("candidate_id"));
                Long appId = appIdByCandidateId.get(cid);
                if (appId == null) continue;

                Application app = applicationRepository.findById(appId).orElse(null);
                if (app == null) continue;

                List<Interview> existing = interviewRepository.findByApplicationId(app.getId());
                if (!existing.isEmpty()) {
                    log.info("Candidate already has an interview for application {}, skipping", app.getId());
                    continue;
                }

                LocalDateTime scheduledAt = toServerLocalDateTime(String.valueOf(slot.get("start_at")));
                Interview interview = Interview.builder()
                        .organization(app.getOrganization())
                        .application(app)
                        .interviewType(Interview.InterviewType.AGENT)
                        .status(Interview.InterviewStatus.SCHEDULED)
                        .scheduledAt(scheduledAt)
                        .jitsiRoomId("interview-" + UUID.randomUUID().toString().substring(0, 8))
                        .round(round)
                        .frontendBaseUrl(baseUrl)
                        .build();
                interview = interviewRepository.save(interview);

                Candidate candidate = app.getCandidate();
                String name = candidate.getFirstName() + " " + (candidate.getLastName() != null ? candidate.getLastName() : "");
                String email = candidate.getEmail();
                if (email != null && !email.isEmpty()) {
                    emailService.sendScheduledSlotEmail(
                            email, name,
                            job != null && job.getTitle() != null ? job.getTitle() : "Interview",
                            round != null && !round.isBlank() ? round : "Technical Round 1",
                            scheduledAt,
                            baseUrl + "/system-check");
                }

                Map<String, Object> row = new HashMap<>();
                row.put("interviewId", interview.getId());
                row.put("candidateId", cid);
                row.put("name", name);
                row.put("email", email);
                row.put("score", slot.get("score"));
                row.put("rank", slot.get("rank"));
                row.put("scheduledAt", scheduledAt.toString());
                row.put("interviewUrl", "/interview/" + interview.getId());
                created.add(row);
                log.info("Scheduled screened interview {} for {} at {}", interview.getId(), name, scheduledAt);
            } catch (Exception e) {
                log.warn("Failed to schedule interview for slot {}: {}", slot.get("candidate_id"), e.getMessage());
            }
        }

        batch.put("confirmed", true);
        batch.put("interviewsScheduled", created.size());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("batchId", batchId);
        response.put("interviewsScheduled", created.size());
        response.put("interviews", created);
        response.put("schedule", schedule);
        return response;
    }

    private LocalDateTime toServerLocalDateTime(String iso) {
        if (iso == null || iso.isEmpty()) return LocalDateTime.now().plusMinutes(5);
        try {
            OffsetDateTime offset = OffsetDateTime.parse(iso);
            return offset.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now().plusMinutes(5);
        }
    }
}