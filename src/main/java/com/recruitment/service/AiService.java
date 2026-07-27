package com.recruitment.service;

import com.recruitment.dto.*;
import com.recruitment.model.Interview;
import com.recruitment.model.InterviewTranscript;
import com.recruitment.repository.InterviewRepository;
import com.recruitment.repository.InterviewTranscriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final WebClient webClient;

    private final InterviewRepository interviewRepository;
    private final InterviewTranscriptRepository interviewTranscriptRepository;

    @Value("${frontend.url}")
    private String frontendUrl;

    public AiScreenResponse screenResume(ResumeScreenRequest request) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("resume_text", request.getResumeText());
            body.put("job_description", request.getJobDescription());

            return webClient.post()
                    .uri("/api/ai/screen-resume")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AiScreenResponse.class)
                    .timeout(Duration.ofSeconds(70))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("AI service error during resume screening: {}", e.getMessage());
            return AiScreenResponse.builder()
                    .score(0.0)
                    .analysis("AI service unavailable. Please try again later.")
                    .recommendation("Unable to process at this time")
                    .build();
        } catch (Exception e) {
            log.error("Timeout or connection error during resume screening: {}", e.getMessage());
            return AiScreenResponse.builder()
                    .score(0.0)
                    .analysis("AI service timed out. Please try again later.")
                    .recommendation("Unable to process at this time")
                    .build();
        }
    }

    public AiMatchResponse matchJobs(Long orgId, String resumeText) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("resume_text", resumeText);
            body.put("org_id", orgId);

            return webClient.post()
                    .uri("/api/ai/match-jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AiMatchResponse.class)
                    .timeout(Duration.ofSeconds(70))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("AI service error during job matching: {}", e.getMessage());
            return AiMatchResponse.builder()
                    .matches(Collections.emptyList())
                    .build();
        } catch (Exception e) {
            log.error("Timeout or connection error during job matching: {}", e.getMessage());
            return AiMatchResponse.builder()
                    .matches(Collections.emptyList())
                    .build();
        }
    }

    public AiAnalysisResponse analyzeApplication(Long applicationId) {
        try {
            Map<String, Long> body = new HashMap<>();
            body.put("application_id", applicationId);

            return webClient.post()
                    .uri("/api/ai/analyze-application")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AiAnalysisResponse.class)
                    .timeout(Duration.ofSeconds(70))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("AI service error during application analysis: {}", e.getMessage());
            return AiAnalysisResponse.builder()
                    .score(0.0)
                    .analysis("AI service unavailable.")
                    .recommendation("Unable to analyze at this time")
                    .build();
        } catch (Exception e) {
            log.error("Timeout during application analysis: {}", e.getMessage());
            return AiAnalysisResponse.builder()
                    .score(0.0)
                    .analysis("AI service timed out.")
                    .recommendation("Unable to analyze at this time")
                    .build();
        }
    }

    public List<String> generateInterviewQuestions(Long interviewId) {
        try {
            Interview interview = interviewRepository.findById(interviewId).orElse(null);
            if (interview == null) {
                return Collections.singletonList("Tell me about yourself and your experience.");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("interview_id", interviewId);

            List<String> questions = webClient.post()
                    .uri("/api/ai/generate-questions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(70))
                    .collectList()
                    .block();

            return (questions != null && !questions.isEmpty())
                    ? questions
                    : getDefaultQuestions();
        } catch (Exception e) {
            log.error("Error generating interview questions: {}", e.getMessage());
            return getDefaultQuestions();
        }
    }

    public String chatWithCandidate(String systemPrompt, List<Map<String, String>> conversationHistory) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("system_prompt", systemPrompt);
            body.put("messages", conversationHistory);

            return webClient.post()
                    .uri("/api/ai/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(70))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("AI chat service error: {}", e.getMessage());
            return "I apologize, but I'm experiencing technical difficulties. Could you please repeat your question?";
        } catch (Exception e) {
            log.error("AI chat timeout: {}", e.getMessage());
            return "I apologize, but the response timed out. Could you please try again?";
        }
    }

    private List<String> getDefaultQuestions() {
        return List.of(
                "Tell me about yourself and your professional background.",
                "What are your key strengths for this role?",
                "Describe a challenging project you worked on and how you handled it.",
                "Where do you see yourself in 5 years?",
                "Why are you interested in this position?"
        );
    }

    public void saveTranscript(Long interviewId, String speaker, String content, Integer questionNumber) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));

        InterviewTranscript transcript = InterviewTranscript.builder()
                .interview(interview)
                .speaker(speaker)
                .content(content)
                .timestamp(LocalDateTime.now())
                .questionNumber(questionNumber)
                .build();
        interviewTranscriptRepository.save(transcript);
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class AiScreenResponse {
        private Double score;
        private String analysis;
        private String recommendation;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class AiMatchResponse {
        private List<Map<String, Object>> matches;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class AiAnalysisResponse {
        private Double score;
        private String analysis;
        private String recommendation;
    }
}
