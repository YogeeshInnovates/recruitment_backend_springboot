package com.recruitment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key}")
    private String apiKey;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(aiServiceUrl)
                .filter(addApiKeyHeader())
                .build();
    }

    @Bean
    public WebClient emailApiWebClient() {
        return WebClient.builder().build();
    }

    private ExchangeFilterFunction addApiKeyHeader() {
        return (request, next) -> {
            ClientRequest filteredRequest = ClientRequest.from(request)
                    .header("X-Internal-Api-Key", apiKey)
                    .build();
            return next.exchange(filteredRequest);
        };
    }
}
