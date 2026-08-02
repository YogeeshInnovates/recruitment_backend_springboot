package com.recruitment.controller;

import com.recruitment.model.*;
import com.recruitment.repository.*;
import com.recruitment.service.EmailService;
import com.recruitment.service.ResumeParserService;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/interview")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class InterviewSetupController {

    private final ResumeParserService resumeParserService;
    private final OrganizationRepository organizationRepository;
    private final JobPostRepository jobPostRepository;
    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final EmailService emailService;
    private final WebClient webClient;

    @Value("${frontend.url}")
    private String frontendUrl;

    @PostMapping("/setup")
    public ResponseEntity<Map<String, Object>> setupInterview(
            @RequestParam("jobDescription") String jobDescription,
            @RequestParam("resume") MultipartFile resumeFile) {

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
                    .build();
            interview = interviewRepository.save(interview);

            String interviewUrl = frontendUrl + "/interview/" + interview.getId();

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
            response.put("scheduledAt", interview.getScheduledAt());
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

                webClient.post()
                        .uri("/api/ai/interview/setup")
                        .bodyValue(aiSetupBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(5))
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

        Application application = interview.getApplication();
        Candidate candidate = application.getCandidate();
        JobPost job = application.getJobPost();

        String candidateName = candidate.getFirstName() + " " + (candidate.getLastName() != null ? candidate.getLastName() : "");

        Map<String, Object> body = new HashMap<>();
        body.put("interview_id", String.valueOf(interviewId));
        body.put("latest_user_message", request.getMessage());
        body.put("question_number", request.getQuestionNumber());

        try {
            Map<String, Object> aiResponse = webClient.post()
                    .uri("/api/ai/interview/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            Map<String, Object> response = new HashMap<>();
            response.put("response", aiResponse.get("response"));
            response.put("question_number", aiResponse.get("question_number"));
            response.put("current_difficulty", aiResponse.get("current_difficulty"));
            response.put("running_score", aiResponse.get("running_score"));
            response.put("is_finished", aiResponse.get("is_finished"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("AI chat error: {}", e.getMessage());
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

        interview.setStatus(Interview.InterviewStatus.IN_PROGRESS);
        interview.setStartedAt(LocalDateTime.now());
        interviewRepository.save(interview);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        return ResponseEntity.ok(response);
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
