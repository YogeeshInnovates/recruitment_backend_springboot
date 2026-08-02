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
import org.springframework.stereotype.Service;

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

    private final InterviewRepository interviewRepository;
    private final EmailService emailService;

    @Value("${frontend.url}")
    private String frontendUrl;

    public List<Interview> allocateSlots(Organization org, JobPost job,
                                         List<Application> applications, String round) {
        List<Interview> created = new ArrayList<>();

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
                        .build();
                interview = interviewRepository.save(interview);

                Candidate candidate = app.getCandidate();
                String name = candidate.getFirstName() + " "
                        + (candidate.getLastName() != null ? candidate.getLastName() : "");
                String email = candidate.getEmail();
                if (email != null && !email.isEmpty()) {
                    emailService.sendScheduledSlotEmail(
                            email, name,
                            job.getTitle() != null ? job.getTitle() : "Interview",
                            round != null ? round : "Technical Round 1",
                            slotStart,
                            frontendUrl + "/system-check");
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
