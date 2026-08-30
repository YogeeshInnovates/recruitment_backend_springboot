package com.recruitment.service;

import com.recruitment.model.PasswordResetToken;
import com.recruitment.model.User;
import com.recruitment.repository.PasswordResetTokenRepository;
import com.recruitment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_TTL_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailService emailService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Transactional
    public void requestReset(String email, String origin) {
        String normalized = email.toLowerCase().trim();
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null) {
            return;
        }
        resetTokenRepository.deleteByUserId(user.getId());
        String token = UUID.randomUUID().toString().replace("-", "");
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES))
                .used(false)
                .build();
        resetTokenRepository.save(resetToken);

        String base = EmailService.resolveBaseUrl(frontendUrl, origin);
        if (base.isEmpty()) {
            base = "http://localhost:3000";
        }
        String resetUrl = base + "/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), resetUrl);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));
        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This reset link has expired. Please request a new one.");
        }
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("This account no longer exists."));
        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        userRepository.save(user);
        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
        resetTokenRepository.deleteByUserId(user.getId());
    }
}