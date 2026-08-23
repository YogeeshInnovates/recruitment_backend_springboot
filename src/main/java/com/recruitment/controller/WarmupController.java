package com.recruitment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class WarmupController {

    private final WebClient webClient;
    private volatile boolean fastapiReady = false;
    private volatile long lastCheckTime = 0;

    @GetMapping("/api/warmup")
    public ResponseEntity<Map<String, Object>> warmup() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ready");
        response.put("spring", true);

        long now = System.currentTimeMillis();
        if (!fastapiReady && (now - lastCheckTime) > 15000) {
            lastCheckTime = now;
            new Thread(() -> {
                try {
                    webClient.get()
                            .uri("/health")
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(30))
                            .block();
                    fastapiReady = true;
                    log.info("FastAPI is now awake");
                } catch (Exception e) {
                    log.warn("FastAPI wakeup ping failed: {}", e.getMessage());
                }
            }).start();
        }

        response.put("fastapi", fastapiReady);
        return ResponseEntity.ok(response);
    }
}
