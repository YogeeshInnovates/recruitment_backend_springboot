package com.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.model.CandidateActivityLog;
import com.recruitment.model.MalpracticeReport;
import com.recruitment.repository.CandidateActivityLogRepository;
import com.recruitment.repository.InterviewSnapshotRepository;
import com.recruitment.repository.MalpracticeReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MalpracticeReportService {

    private final MalpracticeReportRepository reportRepository;
    private final CandidateActivityLogRepository activityLogRepository;
    private final InterviewSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    private static final Set<String> SUSPICIOUS = Set.of(
            "HEAD_TURN_LEFT", "HEAD_TURN_RIGHT", "LOOK_DOWN", "MULTI_FACE",
            "FACE_LOST", "NO_BLINK", "CAMERA_FROZEN", "GAZE_OFF",
            "TAB_SWITCH", "FULLSCREEN_EXIT", "DEVTOOLS", "ANSWER_PASTED",
            "SUSPICIOUS_FAST_ANSWER", "WINDOW_BLUR", "PAGE_BLUR");

    @Transactional(readOnly = true)
    public MalpracticeReport getOrGenerate(Long interviewId) {
        return reportRepository.findByInterviewId(interviewId).orElseGet(() -> generate(interviewId));
    }

    @Transactional
    public MalpracticeReport generate(Long interviewId) {
        List<CandidateActivityLog> logs = activityLogRepository.findAllByInterviewIdOrderByOccurredAt(interviewId);
        Map<String, Long> counts = new LinkedHashMap<>();
        int total = 0;
        for (CandidateActivityLog alog : logs) {
            if (alog.getEventType() != null && SUSPICIOUS.contains(alog.getEventType())) {
                counts.merge(alog.getEventType(), 1L, Long::sum);
                total++;
            }
        }
        long evidenceCount = snapshotRepository.countByInterviewId(interviewId);

        String severity = "NONE";
        if (total > 0) {
            if (total >= 8 || evidenceCount >= 5) severity = "HIGH";
            else if (total >= 4 || evidenceCount >= 3) severity = "MEDIUM";
            else severity = "LOW";
        }

        String summary = String.format(
                "Interview %d: %d suspicious event(s) recorded and %d evidence snapshot(s) captured.",
                interviewId, total, evidenceCount);
        if (total == 0) summary = "No suspicious activity detected during this interview.";

        String breakdownJson;
        try {
            breakdownJson = objectMapper.writeValueAsString(counts);
        } catch (Exception e) {
            breakdownJson = "{}";
        }

        MalpracticeReport report = MalpracticeReport.builder()
                .interviewId(interviewId)
                .summary(summary)
                .eventBreakdown(breakdownJson)
                .severity(severity)
                .suspiciousEventCount(total)
                .evidenceCount((int) evidenceCount)
                .build();
        try {
            report = reportRepository.save(report);
        } catch (Exception e) {
            log.warn("Malpractice report already exists for interview {}: {}", interviewId, e.getMessage());
            return reportRepository.findByInterviewId(interviewId).orElse(report);
        }
        return report;
    }
}
