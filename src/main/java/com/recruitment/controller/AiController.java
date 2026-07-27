package com.recruitment.controller;

import com.recruitment.dto.*;
import com.recruitment.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/screen-resume")
    public ResponseEntity<ApiResponse<AiService.AiScreenResponse>> screenResume(
            @RequestBody ResumeScreenRequest request) {
        AiService.AiScreenResponse response = aiService.screenResume(request);
        return ResponseEntity.ok(ApiResponse.success("Resume screened successfully", response));
    }

    @PostMapping("/match-jobs")
    public ResponseEntity<ApiResponse<AiService.AiMatchResponse>> matchJobs(
            @RequestBody JobMatchRequest request) {
        AiService.AiMatchResponse response = aiService.matchJobs(0L, request.getResumeText());
        return ResponseEntity.ok(ApiResponse.success("Jobs matched successfully", response));
    }
}
