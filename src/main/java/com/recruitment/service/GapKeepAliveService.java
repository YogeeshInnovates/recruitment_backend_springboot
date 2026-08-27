package com.recruitment.service;

import com.recruitment.model.Interview;
import com.recruitment.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class GapKeepAliveService {

    private static final long KEEP_ALIVE_INTERVAL_SECONDS = 360;
    private static final long MIN_GAP_MINUTES = 10;
    private static final int PING_TIMEOUT_SECONDS = 20;

    private final InterviewRepository interviewRepository;
    private final TaskScheduler taskScheduler;

    @Value("${app.public-url:}")
    private String appPublicUrl;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    private final ConcurrentHashMap<Long, ScheduledFuture<?>> orgKeepAlives = new ConcurrentHashMap<>();

    private final WebClient pingClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(PING_TIMEOUT_SECONDS))
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 8000)))
            .build();

    public void onInterviewEnded(Long orgId, LocalDateTime endedAt) {
        if (orgId == null || endedAt == null) return;
        try {
            Interview next = findNextScheduled(orgId, endedAt);
            if (next == null || next.getScheduledAt() == null) {
                cancelKeepAlive(orgId);
                return;
            }

            long gapMinutes = ChronoUnit.MINUTES.between(endedAt, next.getScheduledAt());
            if (gapMinutes < MIN_GAP_MINUTES) {
                cancelKeepAlive(orgId);
                return;
            }

            LocalDateTime target = next.getScheduledAt();
            Long targetId = next.getId();
            cancelKeepAlive(orgId);

            ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                    () -> keepAliveTick(orgId, targetId, target),
                    Duration.ofSeconds(KEEP_ALIVE_INTERVAL_SECONDS));
            orgKeepAlives.put(orgId, future);
            log.info("Gap keep-alive armed for org {} until {} (gap {} min)", orgId, target, gapMinutes);
        } catch (Exception e) {
            log.warn("Failed to arm gap keep-alive for org {}: {}", orgId, e.getMessage());
        }
    }

    private Interview findNextScheduled(Long orgId, LocalDateTime after) {
        Interview next = null;
        for (Interview iv : interviewRepository.findAllByOrganizationId(orgId)) {
            if (iv.getStatus() != Interview.InterviewStatus.SCHEDULED) continue;
            if (iv.getScheduledAt() == null || !iv.getScheduledAt().isAfter(after)) continue;
            if (next == null || iv.getScheduledAt().isBefore(next.getScheduledAt())) next = iv;
        }
        return next;
    }

    private void keepAliveTick(Long orgId, Long targetId, LocalDateTime target) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Interview next = findNextScheduled(orgId, now.minusMinutes(1));
            if (!now.isBefore(target)
                    || next == null || !next.getId().equals(targetId)) {
                cancelKeepAlive(orgId);
                return;
            }

            if (appPublicUrl != null && !appPublicUrl.isBlank()) {
                ping(appPublicUrl.replaceAll("/+$", "") + "/health", "self");
            }
            if (aiServiceUrl != null && !aiServiceUrl.isBlank()) {
                ping(aiServiceUrl.replaceAll("/+$", "") + "/health", "fastapi");
            }
        } catch (Exception e) {
            log.warn("Gap keep-alive tick failed for org {}: {}", orgId, e.getMessage());
        }
    }

    private void ping(String url, String label) {
        try {
            pingClient.get().uri(url).retrieve().bodyToMono(String.class).block();
            log.debug("Gap keep-alive ping ok ({}) {}", label, url);
        } catch (Exception e) {
            log.warn("Gap keep-alive ping failed ({}) {}: {}", label, url, e.getMessage());
        }
    }

    private void cancelKeepAlive(Long orgId) {
        ScheduledFuture<?> f = orgKeepAlives.remove(orgId);
        if (f != null) {
            f.cancel(false);
            log.info("Gap keep-alive cancelled for org {}", orgId);
        }
    }
}