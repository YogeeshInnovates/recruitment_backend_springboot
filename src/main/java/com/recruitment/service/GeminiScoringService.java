package com.recruitment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiScoringService {

    @Value("${gemini.api-key:}")
    private String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();

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
        return apiKey != null && !apiKey.isBlank();
    }

    public ScoreResult scoreTranscript(String candidateName, String jobTitle, String round,
                                       List<String[]> qaPairs) {
        ScoreResult result = new ScoreResult();
        if (!isEnabled()) {
            result.verdict = "Gemini API key not configured";
            return result;
        }
        if (qaPairs.isEmpty()) {
            result.totalScore = 0;
            result.verdict = "No answers provided by candidate";
            result.success = true;
            return result;
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("You are a strict technical interviewer scoring an AI-conducted voice interview.\n");
            sb.append("Candidate: ").append(candidateName).append("\n");
            sb.append("Job Role: ").append(jobTitle).append("\n");
            sb.append("Round: ").append(round).append("\n\n");
            sb.append("Below are question-answer pairs from the interview. Some answers are empty or say 'no answer' - score those 0.\n");
            sb.append("Answers are speech-to-text transcriptions, so ignore grammar/spelling mistakes; judge only technical content and relevance.\n\n");
            for (int i = 0; i < qaPairs.size(); i++) {
                sb.append("Q").append(i + 1).append(": ").append(qaPairs.get(i)[0]).append("\n");
                sb.append("A").append(i + 1).append(": ").append(qaPairs.get(i)[1].isEmpty() ? "(no answer given)" : qaPairs.get(i)[1]).append("\n\n");
            }
            sb.append("Respond ONLY with valid JSON, no markdown, no extra text:\n");
            sb.append("{\"items\":[{\"index\":1,\"score\":<0-10>,\"remark\":\"<one short sentence>\"}],")
              .append("\"total_score\":<0-100>,\"verdict\":\"<one short overall summary>\"}\n");
            sb.append("Rules: each item max 10 points based on answer quality/relevance/depth. ");
            sb.append("total_score must reflect how many questions were meaningfully answered - ");
            sb.append("e.g. answering only 1 of 6 questions well should yield roughly 15-20/100, not more.\n");

            Map<String, Object> textPart = Map.of("text", sb.toString());
            Map<String, Object> content = Map.of("parts", List.of(textPart));
            Map<String, Object> body = Map.of(
                    "contents", List.of(content),
                    "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 2048)
            );

            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(60))
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
            WebClient client = WebClient.builder()
                    .baseUrl("https://generativelanguage.googleapis.com")
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

            String response = client.post()
                    .uri("/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = mapper.readTree(response);
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            text = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}') + 1;
            if (start >= 0 && end > start) text = text.substring(start, end);

            JsonNode parsed = mapper.readTree(text);
            JsonNode items = parsed.path("items");
            for (JsonNode item : items) {
                QaScore qs = new QaScore();
                int idx = item.path("index").asInt(0);
                if (idx >= 1 && idx <= qaPairs.size()) {
                    qs.question = qaPairs.get(idx - 1)[0];
                    qs.answer = qaPairs.get(idx - 1)[1];
                } else {
                    qs.question = "";
                    qs.answer = "";
                }
                qs.score = Math.min(10, Math.max(0, item.path("score").asInt(0)));
                qs.remark = item.path("remark").asText("");
                result.items.add(qs);
            }
            result.totalScore = Math.min(100, Math.max(0, parsed.path("total_score").asInt(0)));
            result.verdict = parsed.path("verdict").asText("");
            result.success = true;
            log.info("Gemini scoring done: total={}, items={}", result.totalScore, result.items.size());
        } catch (Exception e) {
            log.error("Gemini scoring failed: {}", e.getMessage());
            result.success = false;
            result.verdict = "AI scoring failed: " + e.getMessage();
        }
        return result;
    }
}
