package com.recruitment.controller;

import com.recruitment.dto.ApiResponse;
import com.recruitment.dto.ChatRequest;
import com.recruitment.dto.InterviewRequest;
import com.recruitment.model.Interview;
import com.recruitment.model.InterviewTranscript;
import com.recruitment.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/interviews")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<Interview>> scheduleInterview(
            @PathVariable Long orgId,
            @Valid @RequestBody InterviewRequest request) {
        Interview interview = interviewService.scheduleInterview(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Interview scheduled successfully", interview));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Interview>>> getInterviewsByOrg(@PathVariable Long orgId) {
        List<Interview> interviews = interviewService.getInterviewsByOrg(orgId);
        return ResponseEntity.ok(ApiResponse.success("Interviews retrieved successfully", interviews));
    }

    @GetMapping("/{interviewId}")
    public ResponseEntity<ApiResponse<Interview>> getInterviewById(@PathVariable Long interviewId) {
        Interview interview = interviewService.getInterviewById(interviewId);
        return ResponseEntity.ok(ApiResponse.success("Interview retrieved successfully", interview));
    }

    @PostMapping("/{interviewId}/start")
    public ResponseEntity<ApiResponse<Interview>> startInterview(@PathVariable Long interviewId) {
        Interview interview = interviewService.startInterview(interviewId);
        return ResponseEntity.ok(ApiResponse.success("Interview started successfully", interview));
    }

    @PostMapping("/{interviewId}/end")
    public ResponseEntity<ApiResponse<Interview>> endInterview(
            @PathVariable Long interviewId,
            @RequestBody(required = false) Map<String, Object> body) {
        Double aiScore = null;
        String aiRecommendation = null;
        if (body != null) {
            if (body.containsKey("aiScore") && body.get("aiScore") != null) {
                aiScore = Double.parseDouble(body.get("aiScore").toString());
            }
            if (body.containsKey("aiRecommendation")) {
                aiRecommendation = (String) body.get("aiRecommendation");
            }
        }
        Interview interview = interviewService.endInterview(interviewId, aiScore, aiRecommendation);
        return ResponseEntity.ok(ApiResponse.success("Interview ended successfully", interview));
    }

    @GetMapping("/{interviewId}/transcript")
    public ResponseEntity<ApiResponse<List<InterviewTranscript>>> getTranscript(
            @PathVariable Long interviewId) {
        List<InterviewTranscript> transcripts = interviewService.getTranscript(interviewId);
        return ResponseEntity.ok(ApiResponse.success("Transcript retrieved successfully", transcripts));
    }

    @PostMapping("/{interviewId}/chat")
    public ResponseEntity<ApiResponse<Map<String, String>>> chatWithCandidate(
            @PathVariable Long interviewId,
            @RequestBody ChatRequest request) {
        String aiResponse = interviewService.chatDuringInterview(interviewId, request.getMessage());
        return ResponseEntity.ok(ApiResponse.success("Response generated",
                Map.of("response", aiResponse)));
    }
}
