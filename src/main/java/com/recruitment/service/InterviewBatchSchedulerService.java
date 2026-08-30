package com.recruitment.service;

import com.recruitment.model.Application;
import com.recruitment.model.Candidate;
import com.recruitment.model.Interview;
import com.recruitment.model.JobPost;
import com.recruitment.model.Organization;
import com.recruitment.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewBatchSchedulerService {

    private static final int SLOT_MINUTES = 5;
    private static final int FIRST_SLOT_DELAY_MINUTES = 5;
    private static final int MIN_FUTURE_BUFFER_MINUTES = 2;
    private static final int GET_READY_DELAY_SECONDS = 120;

    private final InterviewRepository interviewRepository;
    private final EmailService emailService;
    private final TaskScheduler taskScheduler;

    @Value("${frontend.url}")
    private String frontendUrl;

    public List<Interview> allocateSlots(Organization org, JobPost job,
                                         List<Application> applications, String round,
                                         String requestOrigin) {
        List<Interview> created = new ArrayList<>();

        String baseUrl = EmailService.resolveBaseUrl(frontendUrl, requestOrigin);
        LocalDateTime base = LocalDateTime.now()
                .plusMinutes(FIRST_SLOT_DELAY_MINUTES)
                .withSecond(0)
                .withNano(0);

        int index = 0;
        for (Application app : applications) {
            try {
                List<Interview> existing = interviewRepository.findByApplicationId(app.getId());
                if (!existing.isEmpty()) {
                    log.info("Candidate already has an interview for application {}, skipping", app.getId());
                    continue;
                }

                LocalDateTime slotStart = base.plusMinutes(SLOT_MINUTES * (long) index);
                index++;

                if (!slotStart.isAfter(LocalDateTime.now().plusMinutes(MIN_FUTURE_BUFFER_MINUTES))) {
                    slotStart = LocalDateTime.now()
                            .plusMinutes(MIN_FUTURE_BUFFER_MINUTES)
                            .withSecond(0)
                            .withNano(0);
                }

                Interview interview = Interview.builder()
                        .organization(org)
                        .application(app)
                        .interviewType(Interview.InterviewType.AGENT)
                        .status(Interview.InterviewStatus.SCHEDULED)
                        .scheduledAt(slotStart)
                        .jitsiRoomId("interview-" + UUID.randomUUID().toString().substring(0, 8))
                        .round(round)
                        .frontendBaseUrl(baseUrl)
                        .build();
                interview = interviewRepository.save(interview);

                Candidate candidate = app.getCandidate();
                String name = candidate.getFirstName() + " "
                        + (candidate.getLastName() != null ? candidate.getLastName() : "");
                String email = candidate.getEmail();
                if (email != null && !email.isEmpty()) {
                    emailService.sendSystemCheckEmail(
                            email, name,
                            job.getTitle() != null ? job.getTitle() : "Interview",
                            round != null ? round : "Technical Round 1",
                            slotStart,
                            baseUrl + "/system-check");

                    emailService.sendScheduledSlotEmail(
                            email, name,
                            job.getTitle() != null ? job.getTitle() : "Interview",
                            round != null ? round : "Technical Round 1",
                            slotStart,
                            baseUrl + "/system-check");

                    final Interview saved = interview;
                    final String candidateEmail = email;
                    final String candidateName = name;
                    taskScheduler.schedule(() -> {
                        try {
                            if (saved.getLinkEmailSentAt() != null) return;
                            String sendResult = emailService.sendGetReadyEmail(
                                    candidateEmail, candidateName,
                                    baseUrl + "/interview/" + saved.getId(),
                                    "RM-" + saved.getId());
                            if (sendResult != null) {
                                log.warn("Delayed get-ready email FAILED for interview {}: {}",
                                        saved.getId(), sendResult);
                                return;
                            }
                            saved.setLinkEmailSentAt(LocalDateTime.now());
                            interviewRepository.save(saved);
                            log.info("Get-ready email sent to {} for interview {} (2min after scheduling)",
                                    candidateEmail, saved.getId());
                        } catch (Exception e) {
                            log.warn("Failed to send delayed get-ready email for interview {}: {}",
                                    saved.getId(), e.getMessage());
                        }
                    }, Instant.now().plusSeconds(GET_READY_DELAY_SECONDS));
                }

                created.add(interview);
                log.info("Allocated interview {} for {} at {}", interview.getId(), email, slotStart);
            } catch (Exception e) {
                log.warn("Failed to allocate slot for application {}: {}", app.getId(), e.getMessage());
            }
        }

        return created;
    }
}
