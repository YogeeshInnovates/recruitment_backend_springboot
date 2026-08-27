package com.recruitment.controller;

import com.recruitment.service.AiScreeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class ScreeningHeartbeatController {

    private final AiScreeningService aiScreeningService;

    @GetMapping("/api/screening-heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat() {
        long pending = aiScreeningService.pendingInterviews();

        boolean fastapi;
        if (pending > 0) {
            fastapi = aiScreeningService.pingFastApi();
        } else {
            fastapi = false;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "awake");
        response.put("pending", pending);
        response.put("fastapi", fastapi);
        response.put("now", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}