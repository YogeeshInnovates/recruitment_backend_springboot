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
    public void sendScheduledSlotEmail(String to, String candidateName, String role,
                                       String round, LocalDateTime scheduledAt,
                                       String systemCheckUrl) {
        String formattedDate = scheduledAt.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"));
        String formattedTime = scheduledAt.format(DateTimeFormatter.ofPattern("hh:mm a"));
        String html = """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                <div style="background:linear-gradient(135deg,#667eea,#764ba2);color:white;padding:30px;text-align:center;"><h1>Interview Scheduled</h1></div>
                <div style="padding:30px;">
                <p>Dear %s,</p>
                <p>Congratulations! You have been shortlisted for the <b>%s</b> position.</p>
                <p style="background:#f3f4f6;padding:15px;border-radius:4px;font-size:15px;color:#1f2937;">
                <b>Round:</b> %s<br>
                <b>Date:</b> %s<br>
                <b>Time:</b> %s</p>

                <p style="margin-top:20px;font-weight:bold;color:#0f172a;">Before your interview, please test your system now:</p>
                <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#7c3aed,#8b5cf6);color:white;text-decoration:none;padding:14px 34px;border-radius:8px;font-weight:bold;margin:10px 0;">Check My System</a>

                <p style="background:#e8f4f8;padding:15px;border-radius:4px;font-size:14px;color:#0c5460;text-align:left;">
                <b>How to prepare:</b><br>
                - Google Chrome is required. If you don't have it, please download it first: <a href="https://www.google.com/chrome/">Download Chrome</a><br>
                - Use a speaker or earphones so you can hear the interviewer clearly<br>
                - Allow camera and microphone access when asked — the interview cannot start without them<br>
                - Stay in a quiet, well-lit place with stable internet<br>
                - You will receive your room join link automatically a few minutes before your slot</p>
                <p>Please be online at your scheduled time. Please do not share this schedule with anyone else.</p>
                </div></div></body></html>
                """.formatted(candidateName, role, round, formattedDate, formattedTime, systemCheckUrl);
        sendEmailSafe(to, "Your Interview is Scheduled - " + role, html);
    }

    @Async
    public void sendInterviewScheduledEmail(String to, String candidateName,
                                             LocalDateTime scheduledAt, String interviewType,
                                             String orgName) {        String formattedDate = scheduledAt.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a"));
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
                                            LocalDateTime scheduledAt, Integer minutesBefore,
                                            String systemCheckUrl) {
        String formattedDate = scheduledAt.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a"));
        String html = """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                <div style="background:linear-gradient(135deg,#f093fb,#f5576c);color:white;padding:30px;text-align:center;"><h1>Interview Reminder</h1></div>
                <div style="padding:30px;">
                <p>Dear %s,</p>
                <p style="font-size:18px;font-weight:bold;color:#856404;background:#fff3cd;border:1px solid #ffc107;padding:15px;border-radius:4px;text-align:center;">
                Your interview starts at %s in %d minutes!</p>

                <p style="margin-top:20px;font-weight:bold;color:#0f172a;">Before your interview, please test your system now:</p>
                <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#7c3aed,#8b5cf6);color:white;text-decoration:none;padding:14px 34px;border-radius:8px;font-weight:bold;margin:10px 0;">Check My System</a>

                <p style="background:#e8f4f8;padding:15px;border-radius:4px;font-size:14px;color:#0c5460;text-align:left;">
                <b>How to prepare:</b><br>
                - Google Chrome is required. If you don't have it, please download it first: <a href="https://www.google.com/chrome/">Download Chrome</a><br>
                - Use a speaker or earphones so you can hear the interviewer clearly<br>
                - Allow camera and microphone access when asked<br>
                - Stay in a quiet, well-lit place with stable internet<br>
                - A separate &quot;Get Ready&quot; email with your room link will arrive 2 minutes before your interview</p>
                </div></div></body></html>
                """.formatted(candidateName, formattedDate, minutesBefore, systemCheckUrl);
        sendEmailSafe(to, "Interview Reminder - Starting in " + minutesBefore + " minutes", html);
    }

    @Async
    public void sendGetReadyEmail(String to, String candidateName, String roomUrl, String roomId) {
        String html = """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                <div style="background:linear-gradient(135deg,#10b981,#059669);color:white;padding:30px;text-align:center;"><h1>Get Ready — Interview Starts Soon</h1></div>
                <div style="padding:30px;">
                <p>Dear %s,</p>
                <p style="font-size:18px;font-weight:bold;color:#1e3a8a;">Your interview is about to begin. Please enter your interview room now.</p>
                <p style="background:#f3f4f6;padding:12px;border-radius:4px;text-align:center;font-size:15px;color:#1f2937;">
                <b>Room ID:</b> %s</p>
                <div style="text-align:center;">
                <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#10b981,#059669);color:white;text-decoration:none;padding:15px 44px;border-radius:8px;font-weight:bold;font-size:18px;margin:18px 0;">Join Interview Room</a>
                </div>
                <p style="font-size:13px;color:#64748b;">
                When you enter, the system will run a quick compatibility check. After that, click &quot;Start Interview&quot;.<br>
                If you arrive before your scheduled time, the system will ask you to wait until your slot begins.<br>
                Your interview can last up to 30 minutes. If you do not enter before your slot ends, the interview link will no longer work.</p>
                </div></div></body></html>
                """.formatted(candidateName, roomId, roomUrl);
        sendEmailSafe(to, "Get Ready - Your Interview Room is Open", html);
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
