package com.recruitment.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@Service
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final boolean enabled;

    public CloudinaryService(@Value("${cloudinary.cloud-name:}") String cloudName,
                             @Value("${cloudinary.api-key:}") String apiKey,
                             @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.enabled = cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
        this.cloudinary = enabled ? new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        )) : null;
        if (!enabled) {
            log.warn("Cloudinary credentials not configured - evidence snapshots will be logged, not uploaded.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String uploadEvidence(MultipartFile file, Long interviewId, String eventType) throws IOException {
        if (!enabled) {
            log.info("[EVIDENCE-OFFLINE] interviewId={} eventType={} fileName={} size={}",
                    interviewId, eventType, file.getOriginalFilename(), file.getSize());
            return null;
        }
        File temp = File.createTempFile("evidence_", ".jpg");
        try {
            Files.copy(file.getInputStream(), temp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Map<?, ?> result = cloudinary.uploader().upload(temp, ObjectUtils.asMap(
                    "folder", "recruitment/evidence",
                    "public_id", "interview_" + interviewId + "_" + eventType.toLowerCase() + "_" + System.currentTimeMillis(),
                    "resource_type", "image"
            ));
            return (String) result.get("secure_url");
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }
}
