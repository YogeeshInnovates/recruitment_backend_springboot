package com.recruitment.service;

import com.recruitment.dto.InterviewRequest;
import com.recruitment.exception.ResourceNotFoundException;
import com.recruitment.model.*;
import com.recruitment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final OrganizationRepository organizationRepository;
    private final InterviewTranscriptRepository interviewTranscriptRepository;
    private final AiService aiService;
    private final EmailService emailService;

    @Value("${frontend.url}")
    private String frontendUrl;

    public Interview scheduleInterview(Long orgId, InterviewRequest request) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + orgId));
        Application application = applicationRepository.findById(request.getInterviewType() != null ? request.getApplicationId() : request.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + request.getApplicationId()));

        String roomId = "interview-" + UUID.randomUUID().toString().substring(0, 8);

        Interview interview = Interview.builder()
                .organization(organization)
                .application(application)
                .interviewType(Interview.InterviewType.valueOf(request.getInterviewType()))
                .status(Interview.InterviewStatus.SCHEDULED)
                .scheduledAt(request.getScheduledAt())
                .jitsiRoomId(roomId)
                .build();

        Interview saved = interviewRepository.save(interview);

        Candidate candidate = application.getCandidate();
        String candidateEmail = candidate.getEmail();
        String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
        String orgName = organization.getName();

        if (request.getScheduledAt() != null) {
            emailService.sendInterviewScheduledEmail(
                    candidateEmail, candidateName, request.getScheduledAt(),
                    request.getInterviewType(), orgName
            );
        }

        log.info("Interview scheduled for application {} with type {}", request.getApplicationId(), request.getInterviewType());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Interview> getInterviewsByOrg(Long orgId) {
        return interviewRepository.findAllByOrganizationId(orgId);
    }

    @Transactional(readOnly = true)
    public Interview getInterviewById(Long id) {
        return interviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<InterviewTranscript> getTranscript(Long interviewId) {
        return interviewTranscriptRepository.findAllByInterviewIdOrderByQuestionNumberAsc(interviewId);
    }

    public Interview startInterview(Long id) {
        Interview interview = getInterviewById(id);
        interview.setStatus(Interview.InterviewStatus.IN_PROGRESS);
        interview.setStartedAt(LocalDateTime.now());
        return interviewRepository.save(interview);
    }

    public Interview endInterview(Long id, Double aiScore, String aiRecommendation) {
        Interview interview = getInterviewById(id);
        interview.setStatus(Interview.InterviewStatus.COMPLETED);
        interview.setEndedAt(LocalDateTime.now());
        interview.setAiScore(aiScore);
        interview.setAiRecommendation(aiRecommendation);
        return interviewRepository.save(interview);
    }

    public String chatDuringInterview(Long interviewId, String userMessage) {
        Interview interview = getInterviewById(interviewId);
        if (interview.getStatus() != Interview.InterviewStatus.IN_PROGRESS) {
            throw new IllegalStateException("Interview is not in progress");
        }

        List<InterviewTranscript> existingTranscripts =
                interviewTranscriptRepository.findAllByInterviewIdOrderByQuestionNumberAsc(interviewId);

        List<Map<String, String>> conversationHistory = new ArrayList<>();
        for (InterviewTranscript t : existingTranscripts) {
            Map<String, String> msg = new HashMap<>();
            msg.put("role", t.getSpeaker().equals("candidate") ? "user" : "assistant");
            msg.put("content", t.getContent());
            conversationHistory.add(msg);
        }

        int nextQuestionNumber = existingTranscripts.size() + 1;

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        conversationHistory.add(userMsg);

        aiService.saveTranscript(interviewId, "candidate", userMessage, nextQuestionNumber);

        String systemPrompt = buildInterviewSystemPrompt(interview);
        String aiResponse = aiService.chatWithCandidate(systemPrompt, conversationHistory);

        aiService.saveTranscript(interviewId, "ai_agent", aiResponse, nextQuestionNumber + 1);

        return aiResponse;
    }

    private String buildInterviewSystemPrompt(Interview interview) {
        Application application = interview.getApplication();
        JobPost jobPost = application.getJobPost();
        Candidate candidate = application.getCandidate();

        return """
                You are an AI interview assistant conducting a professional job interview.
                
                Position: %s
                Company: %s
                
                Candidate: %s %s
                Skills: %s
                Experience: %s
                
                Your role is to:
                1. Ask relevant technical and behavioral questions for this position
                2. Evaluate the candidate's responses professionally
                3. Maintain a conversational yet professional tone
                4. Ask follow-up questions based on responses
                5. At the end, provide a summary evaluation
                
                Be professional, encouraging, and thorough in your questioning.
                """.formatted(
                jobPost.getTitle(),
                jobPost.getCompany(),
                candidate.getFirstName(), candidate.getLastName(),
                candidate.getSkills() != null ? candidate.getSkills() : "Not specified",
                candidate.getExperience() != null ? candidate.getExperience() : "Not specified"
        );
    }
}
