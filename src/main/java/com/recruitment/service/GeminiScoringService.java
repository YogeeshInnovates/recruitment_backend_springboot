package com.recruitment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiScoringService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiScoringService(WebClient webClient) {
        this.webClient = webClient;
    }

    public static class QaScore {
        public String question;
        public String answer;
        public int score;
        public String remark;
    }

    public static class ScoreResult {
        public List<QaScore> items = new ArrayList<>();
        public int totalScore;
        public String verdict;
        public boolean success;
    }

    public boolean isEnabled() {
        return true;
    }

    public ScoreResult scoreTranscript(String candidateName, String jobTitle, String round,
                                       List<String[]> qaPairs) {
        ScoreResult result = new ScoreResult();
        if (qaPairs.isEmpty()) {
            result.totalScore = 0;
            result.verdict = "No answers provided by candidate";
            result.success = true;
            return result;
        }

        try {
            List<Map<String, String>> pairs = new ArrayList<>();
            for (String[] qa : qaPairs) {
                Map<String, String> pair = new HashMap<>();
                pair.put("question", qa[0]);
                pair.put("answer", qa[1]);
                pairs.add(pair);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("candidate_name", candidateName);
            body.put("job_title", jobTitle);
            body.put("round", round);
            body.put("qa_pairs", pairs);

            JsonNode root = webClient.post()
                    .uri("/api/ai/interview/score-qa")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            if (root == null) throw new IllegalStateException("Empty response from AI service");

            for (JsonNode item : root.path("items")) {
                QaScore qs = new QaScore();
                qs.question = item.path("question").asText("");
                qs.answer = item.path("answer").asText("");
                qs.score = Math.min(10, Math.max(0, item.path("score").asInt(0)));
                qs.remark = item.path("remark").asText("");
                result.items.add(qs);
            }
            result.totalScore = Math.min(100, Math.max(0, root.path("total_score").asInt(0)));
            result.verdict = root.path("verdict").asText("");
            result.success = root.path("success").asBoolean(false) || !result.items.isEmpty();
            log.info("AI scoring done: total={}, items={}", result.totalScore, result.items.size());
        } catch (Exception e) {
            log.error("AI scoring failed: {}", e.getMessage());
            result.success = false;
            result.verdict = "AI scoring failed: " + e.getMessage();
        }
        return result;
    }
}
