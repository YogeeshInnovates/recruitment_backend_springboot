package com.recruitment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class WarmupController {

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    private volatile boolean fastapiReady = false;
    private final AtomicBoolean wakeupInProgress = new AtomicBoolean(false);

    @GetMapping("/api/warmup")
    public ResponseEntity<Map<String, Object>> warmup() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ready");
        response.put("spring", true);

        if (!fastapiReady && wakeupInProgress.compareAndSet(false, true)) {
            Thread t = new Thread(() -> {
                try {
                    HttpClient httpClient = HttpClient.create()
                            .responseTimeout(Duration.ofSeconds(120))
                            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000);
                    WebClient wakeClient = WebClient.builder()
                            .baseUrl(aiServiceUrl)
                            .clientConnector(new ReactorClientHttpConnector(httpClient))
                            .build();
                    String result = wakeClient.get()
                            .uri("/health")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();
                    fastapiReady = true;
                    log.info("FastAPI is now awake: {}", result);
                } catch (Exception e) {
                    log.warn("FastAPI wakeup ping failed (will retry): {}", e.getMessage());
                } finally {
                    wakeupInProgress.set(false);
                }
            });
            t.setDaemon(true);
            t.start();
        }

        response.put("fastapi", fastapiReady);
        return ResponseEntity.ok(response);
    }
}
