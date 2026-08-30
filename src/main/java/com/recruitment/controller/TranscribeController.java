package com.recruitment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TranscribeController {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/interview/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> transcribe(@RequestParam("file") MultipartFile file)
            throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "audio.webm";
        MediaType contentType = file.getContentType() != null
                ? MediaType.parseMediaType(file.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        byte[] bytes = file.getBytes();

        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", resource).filename(filename).contentType(contentType);

        return webClient.post()
                .uri("/api/ai/transcribe")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = objectMapper.readValue(body, Map.class);
                        Object text = data.get("text");
                        return ResponseEntity.ok(Map.<String, Object>of("text", text == null ? "" : text));
                    } catch (Exception e) {
                        return ResponseEntity.status(502).body(Map.<String, Object>of("success", false, "error", "Invalid response from AI service"));
                    }
                })
                .onErrorResume(e -> Mono.just(ResponseEntity
                        .status(502)
                        .body(Map.<String, Object>of("success", false, "error", "Transcription service unavailable: " + e.getMessage()))));
    }
}