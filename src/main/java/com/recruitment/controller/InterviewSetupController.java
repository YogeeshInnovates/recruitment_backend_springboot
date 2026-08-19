package com.recruitment.controller;

import com.recruitment.model.*;
import com.recruitment.repository.*;
import com.recruitment.service.EmailService;
import com.recruitment.service.ResumeParserService;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/interview")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class InterviewSetupController {

    private static final long MAX_DURATION_MINUTES = 30;

    private final ResumeParserService resumeParserService;
    private final OrganizationRepository organizationRepository;
    private final JobPostRepository jobPostRepository;
    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewTranscriptRepository interviewTranscriptRepository;
    private final CandidateActivityLogRepository candidateActivityLogRepository;
    private final EmailService emailService;
    private final WebClient webClient;

    @Value("${frontend.url}")
    private String frontendUrl;

    @PostMapping("/setup")
    public ResponseEntity<Map<String, Object>> setupInterview(
            @RequestParam("jobDescription") String jobDescription,
            @RequestParam("resume") MultipartFile resumeFile,
            @RequestParam(value = "round", defaultValue = "Technical Round 1") String round,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = "Referer", required = false) String referer) {

        try {
            ResumeParserService.ParsedResume parsed = resumeParserService.parse(resumeFile);

            log.info("Parsed resume - Name: {}, Email: {}, Experience: {}",
                    parsed.getName(), parsed.getEmail(), parsed.getExperience());

            Organization org = Organization.builder()
                    .name("AI Recruitment Platform")
                    .description("Automated AI-powered recruitment")
                    .industry("Technology")
                    .build();
            org = organizationRepository.save(org);

            JobPost job = JobPost.builder()
                    .organization(org)
                    .title("Interview Position")
                    .description(jobDescription)
                    .company("AI Recruitment")
                    .requiredSkills(parsed.getSkills())
                    .experienceRequired(parsed.getExperience())
                    .employmentType(JobPost.EmploymentType.FULL_TIME)
                    .status(JobPost.JobStatus.ACTIVE)
                    .interviewRound(round)
                    .build();
            job = jobPostRepository.save(job);

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

            String roomId = "interview-" + UUID.randomUUID().toString().substring(0, 8);

            Interview interview = Interview.builder()
                    .organization(org)
                    .application(application)
                    .interviewType(Interview.InterviewType.AGENT)
                    .status(Interview.InterviewStatus.SCHEDULED)
                    .scheduledAt(LocalDateTime.now().plusMinutes(5))
                    .jitsiRoomId(roomId)
                    .frontendBaseUrl(EmailService.resolveBaseUrl(frontendUrl,
                            origin != null && !origin.isBlank() ? origin : referer))
                    .build();
            interview = interviewRepository.save(interview);

            String interviewUrl = (interview.getFrontendBaseUrl() == null || interview.getFrontendBaseUrl().isEmpty()
                    ? frontendUrl : interview.getFrontendBaseUrl())
                    + "/interview/" + interview.getId();

            if (parsed.getEmail() != null && !parsed.getEmail().isEmpty()) {
                emailService.sendInterviewLinkEmail(
                        parsed.getEmail(),
                        parsed.getName(),
                        interviewUrl,
                        5
                );
            }

            Map<String, Object> response = new HashMap<>();
            response.put("interviewId", interview.getId());
            response.put("candidateName", parsed.getName());
            response.put("candidateEmail", parsed.getEmail());
            response.put("experience", parsed.getExperience());
            response.put("skills", parsed.getSkills());
            response.put("scheduledAt", interview.getScheduledAt() != null
                    ? interview.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant().toString() : null);
            response.put("jitsiRoomId", roomId);
            response.put("interviewUrl", interviewUrl);
            response.put("jobDescription", jobDescription);
            response.put("resumeText", parsed.getRawText());

            log.info("Interview {} scheduled for candidate {} ({})",
                    interview.getId(), parsed.getName(), parsed.getEmail());

            // Ingest into Pinecone via FastAI setup endpoint
            try {
                Map<String, Object> aiSetupBody = new HashMap<>();
                aiSetupBody.put("interview_id", String.valueOf(interview.getId()));
                aiSetupBody.put("job_description", jobDescription);
                aiSetupBody.put("candidate_resume_text", parsed.getRawText());
                aiSetupBody.put("candidate_name", parsed.getName());
                aiSetupBody.put("max_questions", 15);
                aiSetupBody.put("round", round);

                webClient.post()
                        .uri("/api/ai/interview/setup")
                        .bodyValue(aiSetupBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(90))
                        .block();

                log.info("Pinecone ingestion triggered for interview {}", interview.getId());
            } catch (Exception e) {
                log.warn("Failed to ingest into Pinecone (non-fatal): {}", e.getMessage());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to setup interview: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to setup interview: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{interviewId}")
    public ResponseEntity<Map<String, Object>> getInterview(@PathVariable Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElse(null);

        if (interview == null) {
            return ResponseEntity.notFound().build();
        }

        Application application = interview.getApplication();
        Candidate candidate = application.getCandidate();
        JobPost job = application.getJobPost();

        Map<String, Object> response = new HashMap<>();
        response.put("interviewId", interview.getId());
        response.put("candidateName", candidate.getFirstName() + " " + (candidate.getLastName() != null ? candidate.getLastName() : ""));
        response.put("candidateEmail", candidate.getEmail());
        response.put("experience", candidate.getExperience());
        response.put("skills", candidate.getSkills());
        response.put("resumeText", candidate.getResumeText());
        response.put("jobDescription", job.getDescription());
        response.put("jobTitle", job.getTitle());
        response.put("status", interview.getStatus().name());
        response.put("round", interview.getRound());
        response.put("scheduledAt", interview.getScheduledAt() != null
                ? interview.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant().toString() : null);
        response.put("aiScore", interview.getAiScore());
        response.put("aiRecommendation", interview.getAiRecommendation());
        response.put("jitsiRoomId", interview.getJitsiRoomId());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{interviewId}/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @PathVariable Long interviewId,
            @RequestBody ChatMessageRequest request) {

        Interview interview = interviewRepository.findById(interviewId)
                .orElse(null);

        if (interview == null) {
            return ResponseEntity.notFound().build();
        }

        if (interview.getStartedAt() != null
                && LocalDateTime.now().isAfter(interview.getStartedAt().plusMinutes(MAX_DURATION_MINUTES))) {
            Map<String, Object> response = new HashMap<>();
            response.put("response", "Your interview time is up. Thank you for your time — the interview is now complete.");
            response.put("question_number", request.getQuestionNumber());
            response.put("is_finished", true);
            interview.setStatus(Interview.InterviewStatus.COMPLETED);
            if (interview.getEndedAt() == null) interview.setEndedAt(LocalDateTime.now());
            interviewRepository.save(interview);
            return ResponseEntity.ok(response);
        }

        Application application = interview.getApplication();
        Candidate candidate = application.getCandidate();
        JobPost job = application.getJobPost();

        String candidateName = candidate.getFirstName() + " " + (candidate.getLastName() != null ? candidate.getLastName() : "");

        Map<String, Object> body = new HashMap<>();
        body.put("interview_id", String.valueOf(interviewId));
        body.put("latest_user_message", request.getMessage());
        body.put("question_number", request.getQuestionNumber());

        Map<String, Object> aiResponse = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                aiResponse = webClient.post()
                        .uri("/api/ai/interview/chat")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(60))
                        .block();
                break;
            } catch (Exception chatErr) {
                log.warn("Chat attempt {}/3 failed for interview {}: {}", attempt, interviewId, chatErr.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(attempt * 5000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }

        if (aiResponse != null) {

            Map<String, Object> response = new HashMap<>();
            response.put("response", aiResponse.get("response"));
            response.put("question_number", aiResponse.get("question_number"));
            response.put("current_difficulty", aiResponse.get("current_difficulty"));
            response.put("running_score", aiResponse.get("running_score"));
            response.put("is_finished", aiResponse.get("is_finished"));

            try {
                LocalDateTime now = LocalDateTime.now();
                interviewTranscriptRepository.save(InterviewTranscript.builder()
                        .interview(interview).speaker("candidate")
                        .content(request.getMessage())
                        .timestamp(now).questionNumber(request.getQuestionNumber()).build());
                String aiReply = aiResponse.get("response") != null ? String.valueOf(aiResponse.get("response")) : "";
                if (!aiReply.isEmpty()) {
                    interviewTranscriptRepository.save(InterviewTranscript.builder()
                            .interview(interview).speaker("ai_agent")
                            .content(aiReply)
                            .timestamp(now.plusSeconds(1)).questionNumber(request.getQuestionNumber()).build());
                }
            } catch (Exception saveErr) {
                log.warn("Failed to persist chat transcript: {}", saveErr.getMessage());
            }

            return ResponseEntity.ok(response);

        } else {
            log.error("All 3 chat attempts failed for interview {}", interviewId);
            Map<String, Object> response = new HashMap<>();
            response.put("response", "I apologize for the technical difficulty. Let me continue. Could you please repeat your answer?");
            response.put("question_number", request.getQuestionNumber());
            response.put("is_finished", false);
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/{interviewId}/start")
    public ResponseEntity<Map<String, Object>> startInterview(@PathVariable Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null) return ResponseEntity.notFound().build();

        LocalDateTime now = LocalDateTime.now();
        if (interview.getScheduledAt() != null) {
            if (now.isBefore(interview.getScheduledAt())) {
                long minutesLeft = ChronoUnit.MINUTES.between(now, interview.getScheduledAt());
                long secondsLeft = ChronoUnit.SECONDS.between(now, interview.getScheduledAt()) % 60;
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Please wait. Your interview starts in " + minutesLeft + "m " + secondsLeft + "s.");
                error.put("scheduledAt", interview.getScheduledAt()
                        .atZone(ZoneId.systemDefault()).toInstant().toString());
                return ResponseEntity.badRequest().body(error);
            }
            if (now.isAfter(interview.getScheduledAt().plusMinutes(MAX_DURATION_MINUTES))) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Your interview slot has ended. Please contact the recruiter.");
                error.put("slotClosed", true);
                return ResponseEntity.badRequest().body(error);
            }
        }

        interview.setStatus(Interview.InterviewStatus.IN_PROGRESS);
        interview.setStartedAt(now);
        interviewRepository.save(interview);

        if (interview.getInterviewType() == Interview.InterviewType.AGENT) {
            triggerAiSetup(interview);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        return ResponseEntity.ok(response);
    }

    private void triggerAiSetup(Interview interview) {
        try {
            Application app = interview.getApplication();
            Candidate candidate = app.getCandidate();
            JobPost job = app.getJobPost();

            Map<String, Object> body = new HashMap<>();
            body.put("interview_id", String.valueOf(interview.getId()));
            body.put("job_description", job.getDescription());
            body.put("candidate_resume_text", candidate.getResumeText());
            body.put("candidate_name", candidate.getFirstName()
                    + " " + (candidate.getLastName() != null ? candidate.getLastName() : ""));
            body.put("max_questions", 15);
            body.put("round", interview.getRound() != null ? interview.getRound() : "Technical Round 1");

            webClient.post()
                    .uri("/api/ai/interview/setup")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(90))
                    .block();
            log.info("AI interview session ready for interview {}", interview.getId());
        } catch (Exception e) {
            log.warn("Failed to prepare AI session for interview {} (non-fatal): {}", interview.getId(), e.getMessage());
        }
    }

    @PostMapping("/{interviewId}/activity")
    public ResponseEntity<Map<String, Object>> logActivity(
            @PathVariable Long interviewId,
            @RequestBody ActivityRequest request) {
        if (interviewRepository.findById(interviewId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CandidateActivityLog logEntry = CandidateActivityLog.builder()
                .interviewId(interviewId)
                .eventType(request.getEventType())
                .detail(request.getDetail())
                .occurredAt(LocalDateTime.now())
                .build();
        candidateActivityLogRepository.save(logEntry);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "logged");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{interviewId}/activity/summary")
    public ResponseEntity<Map<String, Object>> activitySummary(@PathVariable Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null) return ResponseEntity.notFound().build();

        List<CandidateActivityLog> logs =
                candidateActivityLogRepository.findAllByInterviewIdOrderByOccurredAt(interviewId);

        Map<String, Integer> counts = new HashMap<>();
        for (CandidateActivityLog l : logs) {
            String type = l.getEventType() != null && !l.getEventType().isEmpty()
                    ? l.getEventType() : "OTHER";
            counts.merge(type, 1, Integer::sum);
        }

        List<Map<String, Object>> events = new ArrayList<>();
        for (CandidateActivityLog l : logs) {
            Map<String, Object> ev = new HashMap<>();
            ev.put("type", l.getEventType());
            ev.put("detail", l.getDetail());
            ev.put("time", l.getOccurredAt() != null ? l.getOccurredAt().toString() : null);
            events.add(ev);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("interviewId", interviewId);
        resp.put("candidateName", "");
        resp.put("email", "");
        try {
            Application app = interview.getApplication();
            if (app != null && app.getCandidate() != null) {
                resp.put("candidateName",
                        app.getCandidate().getFirstName() + " " +
                                (app.getCandidate().getLastName() != null ? app.getCandidate().getLastName() : ""));
                resp.put("email", app.getCandidate().getEmail());
            }
        } catch (Exception ignored) { }
        resp.put("jobTitle", "");
        try {
            Application app = interview.getApplication();
            if (app != null && app.getJobPost() != null) resp.put("jobTitle", app.getJobPost().getTitle());
        } catch (Exception ignored) { }
        resp.put("round", interview.getRound());
        resp.put("status", interview.getStatus() != null ? interview.getStatus().name() : null);
        resp.put("counts", counts);
        resp.put("totalFlags", logs.size());
        resp.put("events", events);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{interviewId}/transcript")
    public ResponseEntity<List<Map<String, Object>>> getTranscript(@PathVariable Long interviewId) {
        List<InterviewTranscript> rows = interviewTranscriptRepository.findByInterviewIdOrderByTimestampAsc(interviewId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (InterviewTranscript t : rows) {
            Map<String, Object> row = new HashMap<>();
            row.put("speaker", t.getSpeaker());
            row.put("content", t.getContent());
            row.put("timestamp", t.getTimestamp() != null ? t.getTimestamp().toString() : null);
            row.put("questionNumber", t.getQuestionNumber());
            out.add(row);
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{interviewId}/report/score")
    public ResponseEntity<byte[]> scoreReport(@PathVariable Long interviewId) throws IOException {
        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null) return ResponseEntity.notFound().build();
        Application app = interview.getApplication();
        Candidate candidate = app.getCandidate();
        JobPost job = app.getJobPost();

        String csv = "Candidate,Email,Job,Round,Status,Overall Score,Recommendation,Started At,Ended At\n"
                + String.join(",", new String[]{
                csvEscape(candidate.getFirstName() + " " + (candidate.getLastName() != null ? candidate.getLastName() : "")),
                csvEscape(candidate.getEmail()),
                csvEscape(job.getTitle()),
                csvEscape(interview.getRound()),
                String.valueOf(interview.getStatus()),
                String.valueOf(interview.getAiScore()),
                csvEscape(interview.getAiRecommendation()),
                String.valueOf(interview.getStartedAt()),
                String.valueOf(interview.getEndedAt())
        }) + "\n"
                + "Notes,\"" + (interview.getNotes() != null ? interview.getNotes().replace("\"", "\"\"") : "") + "\"\n";

        return attachment("interview-" + interviewId + "-score.csv", csv, "text/csv");
    }

    @GetMapping("/{interviewId}/report/transcript")
    public ResponseEntity<byte[]> transcriptReport(@PathVariable Long interviewId) throws IOException {
        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null) return ResponseEntity.notFound().build();
        Application app = interview.getApplication();
        Candidate candidate = app.getCandidate();

        StringBuilder sb = new StringBuilder();
        sb.append("INTERVIEW TRANSCRIPT\n");
        sb.append("====================\n");
        sb.append("Candidate: ").append(candidate.getFirstName()).append(" ")
                .append(candidate.getLastName() != null ? candidate.getLastName() : "").append("\n");
        sb.append("Round: ").append(interview.getRound()).append("\n\n");

        List<InterviewTranscript> rows = interviewTranscriptRepository.findByInterviewIdOrderByTimestampAsc(interviewId);
        if (rows.isEmpty()) {
            for (InterviewTranscript t : interview.getTranscripts()) {
                sb.append(t.getContent()).append("\n\n");
            }
        } else {
            for (InterviewTranscript t : rows) {
                String speaker = "AI Interviewer".equals(t.getSpeaker()) || "ai_agent".equals(t.getSpeaker())
                        ? "AI Interviewer" : "Candidate";
                sb.append("[").append(speaker).append("]\n").append(t.getContent()).append("\n\n");
            }
        }

        return attachment("interview-" + interviewId + "-qa.txt", sb.toString(), "text/plain");
    }

    @GetMapping("/{interviewId}/report/activity")
    public ResponseEntity<byte[]> activityReport(@PathVariable Long interviewId) throws IOException {
        List<CandidateActivityLog> logs = candidateActivityLogRepository.findAllByInterviewIdOrderByOccurredAt(interviewId);
        StringBuilder sb = new StringBuilder();
        sb.append("Event Type,Occurred At,Detail\n");
        for (CandidateActivityLog l : logs) {
            sb.append(csvEscape(l.getEventType())).append(",")
                    .append(String.valueOf(l.getOccurredAt())).append(",")
                    .append(csvEscape(l.getDetail())).append("\n");
        }
        return attachment("interview-" + interviewId + "-activity.csv", sb.toString(), "text/csv");
    }

    private ResponseEntity<byte[]> attachment(String fileName, String content, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        return v.contains(",") || v.contains("\n") ? "\"" + v + "\"" : v;
    }

    @Data
    public static class ActivityRequest {
        private String eventType;
        private String detail;
    }

    @PostMapping("/{interviewId}/end")
    public ResponseEntity<Map<String, Object>> endInterview(@PathVariable Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null) return ResponseEntity.notFound().build();

        interview.setStatus(Interview.InterviewStatus.COMPLETED);
        interview.setEndedAt(LocalDateTime.now());
        interviewRepository.save(interview);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> scoringCallback(@RequestBody Map<String, Object> payload) {
        Object idValue = payload.get("interview_id");
        if (idValue == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Missing interview_id");
            return ResponseEntity.badRequest().body(error);
        }

        Long interviewId;
        try {
            interviewId = Long.parseLong(String.valueOf(idValue).trim());
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid interview_id: " + idValue);
            return ResponseEntity.badRequest().body(error);
        }

        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Double overall = toDouble(payload.get("overall_score"));
            Double technical = toDouble(payload.get("technical_score"));
            Double communication = toDouble(payload.get("communication_score"));
            String recommendation = payload.get("recommendation") != null
                    ? String.valueOf(payload.get("recommendation")) : null;
            String summary = payload.get("summary") != null
                    ? String.valueOf(payload.get("summary")) : null;

            interview.setAiScore(overall);
            interview.setAiRecommendation(recommendation);
            interview.setNotes(buildScoringNotes(overall, technical, communication,
                    payload.get("strengths"), payload.get("weaknesses"), summary,
                    payload.get("question_scores"), payload.get("accumulated_score")));
            interview.setStatus(Interview.InterviewStatus.COMPLETED);
            if (interview.getEndedAt() == null) {
                interview.setEndedAt(LocalDateTime.now());
            }

            Object transcriptObj = payload.get("transcript_sheet");
            if (transcriptObj != null && !String.valueOf(transcriptObj).isEmpty()) {
                InterviewTranscript transcript = InterviewTranscript.builder()
                        .interview(interview)
                        .speaker("AI-INTERVIEW")
                        .content(String.valueOf(transcriptObj))
                        .timestamp(LocalDateTime.now())
                        .build();
                interview.getTranscripts().add(transcript);
            }

            interviewRepository.save(interview);

            log.info("Scoring callback applied for interview {}", interviewId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "scored");
            response.put("interviewId", interviewId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to process scoring callback for interview {}: {}", interviewId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to process scoring callback: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildScoringNotes(Double overall, Double technical, Double communication,
                                     Object strengths, Object weaknesses, String summary,
                                     Object questionScores, Object accumulatedScore) {
        StringBuilder sb = new StringBuilder();
        if (overall != null) sb.append("Overall Score: ").append(overall).append("\n");
        if (technical != null) sb.append("Technical Score: ").append(technical).append("\n");
        if (communication != null) sb.append("Communication Score: ").append(communication).append("\n");
        if (questionScores != null) sb.append("Per-Question Scores: ").append(questionScores).append("\n");
        if (accumulatedScore != null) sb.append("Accumulated Score: ").append(accumulatedScore).append("\n");
        if (strengths != null) sb.append("Strengths: ").append(strengths).append("\n");
        if (weaknesses != null) sb.append("Weaknesses: ").append(weaknesses).append("\n");
        if (summary != null && !summary.isEmpty()) sb.append("Summary: ").append(summary);
        return sb.toString();
    }

    private String buildSystemPrompt(String candidateName, String experience,
                                      String skills, String resumeText,
                                      String jobDescription, String jobTitle) {
        String timeOfDay = getTimeOfDay();

        String shortJobDesc = jobDescription != null && jobDescription.length() > 500 ? jobDescription.substring(0, 500) + "..." : jobDescription;

        return "You are a professional AI interviewer conducting a live VOICE job interview.\n\n"
                + "JOB: " + jobTitle + "\n"
                + "JOB DESCRIPTION: " + shortJobDesc + "\n\n"
                + "CANDIDATE: " + candidateName + " | Experience: " + experience + " | Skills: " + skills + "\n\n"
                + "CRITICAL RULES:\n"
                + "1. You are the INTERVIEWER only. You ASK questions. You NEVER answer technical questions.\n"
                + "2. Keep responses SHORT - max 1-2 sentences. Under 25 words.\n"
                + "3. ACCEPT every answer the candidate gives. Say \"Got it\", \"Thanks\", \"I see\" before next question.\n"
                + "4. If candidate hesitates, encourage: \"Yes, go on, try to explain\". ONE chance only.\n"
                + "5. If they can't answer or say they don't know, say \"That's okay, let's move on\".\n"
                + "6. NEVER provide hints or correct answers.\n"
                + "7. One question at a time. Never interrupt.\n\n"
                + "FLOW: Greet → Confirm name → Brief guidelines → Ask 6-8 questions → End interview.\n"
                + "TONE: Professional, friendly, patient. Like a real phone interview.\n";
    }

    private String getTimeOfDay() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 6 && hour < 12) return "morning";
        if (hour >= 12 && hour < 17) return "afternoon";
        return "evening";
    }

    @Data
    public static class ChatMessageRequest {
        private String message;
        private List<Map<String, String>> conversationHistory;
        private int questionNumber;
    }
}
