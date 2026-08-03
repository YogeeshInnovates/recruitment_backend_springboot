package com.recruitment.controller;

import com.recruitment.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DiagnosticsController {

    private final EmailService emailService;

    @GetMapping("/test-email")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN', 'HR', 'RECRUITER')")
    public ResponseEntity<Map<String, Object>> testEmail(@RequestParam("to") String to) {
        String error = emailService.sendTestEmail(to);
        Map<String, Object> response = new HashMap<>();
        response.put("ok", error == null);
        response.put("error", error);
        return ResponseEntity.ok(response);
    }
}
