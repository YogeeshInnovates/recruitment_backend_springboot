package com.recruitment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class FastApiKeepAlive {

    private final WebClient webClient;

    @Scheduled(fixedRate = 600000)
    public void ping() {
        try {
            webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .block();
            log.info("FastAPI keep-alive OK");
        } catch (Exception e) {
            log.warn("FastAPI keep-alive failed (non-fatal): {}", e.getMessage());
        }
    }
}
