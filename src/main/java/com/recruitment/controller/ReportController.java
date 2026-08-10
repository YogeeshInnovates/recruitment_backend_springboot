package com.recruitment.controller;

import com.recruitment.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class ReportController {

    private final InterviewRepository interviewRepository;
    private final WebClient webClient;

    @PostMapping("/{interviewId}/report")
    public ResponseEntity<?> generateReport(@PathVariable Long interviewId,
                                            @RequestBody(required = false) Map<String, Object> payload) {
        if (!interviewRepository.existsById(interviewId)) {
            return ResponseEntity.notFound().build();
        }
        if (payload == null) {
            payload = Map.of();
        }
        try {
            Map<String, Object> body = webClient.post()
                    .uri("/api/ai/interview/report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(120));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "Report service unavailable: " + e.getMessage()));
        }
    }
}
