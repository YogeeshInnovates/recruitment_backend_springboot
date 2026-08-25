package com.recruitment.scheduler;

import com.recruitment.model.Candidate;
import com.recruitment.model.Interview;
import com.recruitment.repository.InterviewRepository;
import com.recruitment.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewScheduler {

    private final InterviewRepository interviewRepository;
    private final EmailService emailService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processScheduledInterviews() {
        log.debug("Running interview scheduler check...");

        List<Interview> scheduledInterviews = interviewRepository.findByStatus(Interview.InterviewStatus.SCHEDULED);
        LocalDateTime now = LocalDateTime.now();

        for (Interview interview : scheduledInterviews) {
            try {
                LocalDateTime scheduledAt = interview.getScheduledAt();
                if (scheduledAt == null) continue;
                long minutesUntil = ChronoUnit.MINUTES.between(now, scheduledAt);

                Candidate candidate = interview.getApplication().getCandidate();
                String candidateEmail = candidate.getEmail();
                String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
                String baseUrl = interview.getFrontendBaseUrl() != null && !interview.getFrontendBaseUrl().isEmpty()
                        ? interview.getFrontendBaseUrl().replaceAll("/+$", "")
                        : EmailService.resolveBaseUrl(frontendUrl, null);
                String roomUrl = baseUrl + "/interview/" + interview.getId();

                if (minutesUntil <= 3 && minutesUntil >= -5 && interview.getLinkEmailSentAt() == null) {
                    log.info("Sending get-ready email for interview {} to candidate: {}",
                            interview.getId(), candidateName);

                    String sendResult = emailService.sendGetReadyEmail(
                            candidateEmail, candidateName, roomUrl,
                            "RM-" + interview.getId()
                    );

                    if (sendResult != null) {
                        log.warn("Get-ready email FAILED for interview {} ({}), will retry next tick: {}",
                                interview.getId(), candidateEmail, sendResult);
                        continue;
                    }

                    interview.setLinkEmailSentAt(now);

                    if (interview.getInterviewType() == Interview.InterviewType.MANUAL) {
                        Long orgId = interview.getOrganization().getId();
                        boolean orgBusy = interviewRepository.existsByOrganizationIdAndStatus(orgId, Interview.InterviewStatus.IN_PROGRESS);
                        if (orgBusy) {
                            log.info("Skipping auto-start for interview {} — org {} already has active interview", interview.getId(), orgId);
                            continue;
                        }
                        String recruiterEmail = interview.getOrganization().getEmail();
                        if (recruiterEmail != null && !recruiterEmail.isEmpty()) {
                            emailService.sendGetReadyEmail(recruiterEmail, "Recruiter",
                                    roomUrl, "RM-" + interview.getId());
                        }
                        interview.setStatus(Interview.InterviewStatus.IN_PROGRESS);
                        interview.setStartedAt(now);
                    }

                    interviewRepository.save(interview);
                }
            } catch (Exception e) {
                log.error("Error processing interview {}: {}", interview.getId(), e.getMessage(), e);
            }
        }
    }
}