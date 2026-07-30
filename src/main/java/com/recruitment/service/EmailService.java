package com.recruitment.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Async
    public void sendInterviewScheduledEmail(String to, String candidateName,
                                             LocalDateTime scheduledAt, String interviewType,
                                             String orgName) {
        String formattedDate = scheduledAt.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a"));
        String html = """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                <div style="background:linear-gradient(135deg,#667eea,#764ba2);color:white;padding:30px;text-align:center;"><h1>Interview Scheduled</h1></div>
                <div style="padding:30px;">
                <p>Dear %s,</p>
                <p>Your interview has been scheduled.</p>
                <p><b>Company:</b> %s<br><b>Date:</b> %s<br><b>Type:</b> %s</p>
                <p>You will receive the meeting link shortly.</p>
                </div></div></body></html>
                """.formatted(candidateName, orgName, formattedDate, interviewType);
        sendEmailSafe(to, "Interview Scheduled - " + orgName, html);
    }

    @Async
    public void sendInterviewReminderEmail(String to, String candidateName,
                                            LocalDateTime scheduledAt, Integer minutesBefore) {
        String formattedDate = scheduledAt.format(DateTimeFormatter.ofPattern("hh:mm a"));
        String html = """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                <div style="background:linear-gradient(135deg,#f093fb,#f5576c);color:white;padding:30px;text-align:center;"><h1>Interview Reminder</h1></div>
                <div style="padding:30px;">
                <p>Dear %s,</p>
                <p style="font-size:18px;font-weight:bold;color:#856404;background:#fff3cd;border:1px solid #ffc107;padding:15px;border-radius:4px;text-align:center;">
                Your interview starts in %d minutes at %s!</p>
                <p>Please be in a quiet environment with stable internet.</p>
                </div></div></body></html>
                """.formatted(candidateName, minutesBefore, formattedDate);
        sendEmailSafe(to, "Interview Reminder - Starting in " + minutesBefore + " minutes", html);
    }

    @Async
    public void sendInterviewLinkEmail(String to, String candidateName, String roomUrl,
                                        Integer minutesBefore) {
        String html = """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                <div style="background:linear-gradient(135deg,#4facfe,#00f2fe);color:white;padding:30px;text-align:center;"><h1>Interview Starting Now!</h1></div>
                <div style="padding:30px;text-align:center;">
                <p>Dear %s,</p>
                <p>Your interview is ready. Click below to join:</p>
                <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#4facfe,#00f2fe);color:white;text-decoration:none;padding:15px 40px;border-radius:8px;font-weight:bold;font-size:18px;margin:20px 0;">Join Interview</a>
                <p style="background:#e8f4f8;padding:15px;border-radius:4px;font-size:14px;color:#0c5460;text-align:left;">
                <b>Tips:</b><br>- Stable internet connection<br>- Quiet, well-lit environment<br>- Test camera & microphone<br>- Have your resume handy</p>
                </div></div></body></html>
                """.formatted(candidateName, roomUrl);
        sendEmailSafe(to, "Your Interview is Ready - Join Now", html);
    }

    private void sendEmailSafe(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom("noreply@recruitment-platform.com");
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("Could not send email to {} (SMTP not configured): {}", to, e.getMessage());
            log.info("=== EMAIL WOULD HAVE BEEN SENT ===");
            log.info("To: {}", to);
            log.info("Subject: {}", subject);
            log.info("=== END EMAIL ===");
        }
    }
}
