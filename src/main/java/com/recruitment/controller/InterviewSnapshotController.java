package com.recruitment.controller;

import com.recruitment.model.Interview;
import com.recruitment.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewSnapshotController {

    private final InterviewRepository interviewRepository;

    private static final ConcurrentHashMap<Long, byte[]> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Instant> SNAPSHOT_TIMES = new ConcurrentHashMap<>();

    @PostMapping("/{interviewId}/snapshot")
    public ResponseEntity<?> uploadSnapshot(@PathVariable Long interviewId,
                                            @RequestBody(required = false) byte[] body) {
        if (body == null || body.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null) {
            return ResponseEntity.notFound().build();
        }
        if (interview.getStatus() == Interview.InterviewStatus.COMPLETED
                || interview.getStatus() == Interview.InterviewStatus.CANCELLED) {
            SNAPSHOTS.remove(interviewId);
            SNAPSHOT_TIMES.remove(interviewId);
            return ResponseEntity.ok().build();
        }
        SNAPSHOTS.put(interviewId, body);
        SNAPSHOT_TIMES.put(interviewId, Instant.now());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{interviewId}/snapshot")
    public ResponseEntity<byte[]> getSnapshot(@PathVariable Long interviewId) {
        byte[] img = SNAPSHOTS.get(interviewId);
        if (img == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.IMAGE_JPEG)
                .body(img);
    }
}
