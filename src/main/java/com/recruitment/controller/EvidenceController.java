package com.recruitment.controller;

import com.recruitment.model.InterviewSnapshot;
import com.recruitment.repository.InterviewSnapshotRepository;
import com.recruitment.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class EvidenceController {

    private final InterviewSnapshotRepository snapshotRepository;
    private final CloudinaryService cloudinaryService;

    @PostMapping("/{interviewId}/evidence")
    public ResponseEntity<?> uploadEvidence(@PathVariable Long interviewId,
                                            @RequestParam("eventType") String eventType,
                                            @RequestParam(value = "image", required = false) MultipartFile image) {
        try {
            String url = null;
            if (image != null && !image.isEmpty()) {
                url = cloudinaryService.uploadEvidence(image, interviewId, eventType);
            }
            InterviewSnapshot snapshot = InterviewSnapshot.builder()
                    .interviewId(interviewId)
                    .eventType(eventType)
                    .cloudinaryUrl(url)
                    .capturedAt(LocalDateTime.now())
                    .build();
            snapshot = snapshotRepository.save(snapshot);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("snapshotId", snapshot.getId());
            body.put("interviewId", interviewId);
            body.put("eventType", eventType);
            body.put("cloudinaryUrl", url);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Evidence upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/{interviewId}/evidence")
    public ResponseEntity<?> listEvidence(@PathVariable Long interviewId) {
        List<InterviewSnapshot> snapshots = snapshotRepository.findAllByInterviewIdOrderByCapturedAtDesc(interviewId);
        List<Map<String, Object>> items = snapshots.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("snapshotId", s.getId());
            m.put("eventType", s.getEventType());
            m.put("cloudinaryUrl", s.getCloudinaryUrl());
            m.put("capturedAt", s.getCapturedAt() == null ? null : s.getCapturedAt().toString());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("interviewId", interviewId, "count", items.size(), "items", items));
    }
}
