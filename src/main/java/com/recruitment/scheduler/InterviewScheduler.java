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
                String roomUrl = frontendUrl + "/interview/" + interview.getId();
                String systemCheckUrl = frontendUrl + "/system-check";

                if (minutesUntil <= 10 && minutesUntil > 5 && interview.getReminderEmailSentAt() == null) {
                    log.info("Sending reminder for interview {} to candidate: {} ({} minutes)",
                            interview.getId(), candidateName, minutesUntil);
                    emailService.sendInterviewReminderEmail(
                            candidateEmail, candidateName, scheduledAt, (int) minutesUntil,
                            systemCheckUrl
                    );
                    interview.setReminderEmailSentAt(now);
                    interviewRepository.save(interview);
                }

                if (minutesUntil <= 2 && minutesUntil >= -1 && interview.getLinkEmailSentAt() == null) {
                    log.info("Sending get-ready email for interview {} to candidate: {}",
                            interview.getId(), candidateName);

                    emailService.sendGetReadyEmail(
                            candidateEmail, candidateName, roomUrl,
                            "RM-" + interview.getId()
                    );

                    interview.setLinkEmailSentAt(now);

                    if (interview.getInterviewType() == Interview.InterviewType.MANUAL) {
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
