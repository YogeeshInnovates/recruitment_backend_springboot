package com.recruitment.service;

import jakarta.mail.MessagingException;
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Interview Scheduled - " + orgName);
            helper.setFrom("noreply@recruitment-platform.com");

            String formattedDate = scheduledAt.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a"));

            String htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <style>
                            body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                            .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                            .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; }
                            .header h1 { margin: 0; font-size: 24px; }
                            .content { padding: 30px; }
                            .detail-box { background-color: #f8f9fa; border-left: 4px solid #667eea; padding: 15px; margin: 15px 0; border-radius: 4px; }
                            .detail-box p { margin: 5px 0; }
                            .label { font-weight: bold; color: #333; }
                            .footer { background-color: #f8f9fa; padding: 20px; text-align: center; font-size: 12px; color: #666; }
                            .btn { display: inline-block; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; text-decoration: none; padding: 12px 30px; border-radius: 5px; font-weight: bold; margin-top: 15px; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Interview Scheduled</h1>
                            </div>
                            <div class="content">
                                <p>Dear %s,</p>
                                <p>We are pleased to inform you that your interview has been scheduled.</p>
                                <div class="detail-box">
                                    <p><span class="label">Company:</span> %s</p>
                                    <p><span class="label">Date &amp; Time:</span> %s</p>
                                    <p><span class="label">Interview Type:</span> %s</p>
                                </div>
                                <p>Please make sure to be available at the scheduled time. You will receive another email with the meeting link closer to the interview time.</p>
                                <p>If you need to reschedule, please contact us as soon as possible.</p>
                            </div>
                            <div class="footer">
                                <p>This is an automated message from the Recruitment Platform. Please do not reply to this email.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(candidateName, orgName, formattedDate, interviewType);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Interview scheduled email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send interview scheduled email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendInterviewReminderEmail(String to, String candidateName,
                                            LocalDateTime scheduledAt, Integer minutesBefore) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Interview Reminder - Starting in " + minutesBefore + " minutes");
            helper.setFrom("noreply@recruitment-platform.com");

            String formattedDate = scheduledAt.format(DateTimeFormatter.ofPattern("hh:mm a"));

            String htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <style>
                            body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                            .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                            .header { background: linear-gradient(135deg, #f093fb 0%%, #f5576c 100%%); color: white; padding: 30px; text-align: center; }
                            .header h1 { margin: 0; font-size: 24px; }
                            .content { padding: 30px; }
                            .alert-box { background-color: #fff3cd; border: 1px solid #ffc107; padding: 15px; margin: 15px 0; border-radius: 4px; text-align: center; font-size: 18px; font-weight: bold; color: #856404; }
                            .footer { background-color: #f8f9fa; padding: 20px; text-align: center; font-size: 12px; color: #666; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Interview Reminder</h1>
                            </div>
                            <div class="content">
                                <p>Dear %s,</p>
                                <div class="alert-box">
                                    Your interview starts in %d minutes at %s!
                                </div>
                                <p>Please make sure you are in a quiet environment with a stable internet connection.</p>
                                <p>Good luck!</p>
                            </div>
                            <div class="footer">
                                <p>This is an automated reminder from the Recruitment Platform.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(candidateName, minutesBefore, formattedDate);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Interview reminder email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send interview reminder email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendInterviewLinkEmail(String to, String candidateName, String roomUrl,
                                        Integer minutesBefore) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Your Interview is Ready - Join Now");
            helper.setFrom("noreply@recruitment-platform.com");

            String htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <style>
                            body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                            .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                            .header { background: linear-gradient(135deg, #4facfe 0%%, #00f2fe 100%%); color: white; padding: 30px; text-align: center; }
                            .header h1 { margin: 0; font-size: 24px; }
                            .content { padding: 30px; text-align: center; }
                            .join-btn { display: inline-block; background: linear-gradient(135deg, #4facfe 0%%, #00f2fe 100%%); color: white; text-decoration: none; padding: 15px 40px; border-radius: 8px; font-weight: bold; font-size: 18px; margin: 20px 0; }
                            .note { background-color: #e8f4f8; padding: 15px; border-radius: 4px; margin: 15px 0; font-size: 14px; color: #0c5460; }
                            .footer { background-color: #f8f9fa; padding: 20px; text-align: center; font-size: 12px; color: #666; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Interview Starting Now!</h1>
                            </div>
                            <div class="content">
                                <p>Dear %s,</p>
                                <p>Your interview is ready. Click the button below to join:</p>
                                <a href="%s" class="join-btn">Join Interview</a>
                                <div class="note">
                                    <strong>Tips for a successful interview:</strong><br>
                                    - Ensure you have a stable internet connection<br>
                                    - Use a quiet, well-lit environment<br>
                                    - Test your camera and microphone beforehand<br>
                                    - Have a copy of your resume handy
                                </div>
                            </div>
                            <div class="footer">
                                <p>This link is valid for the scheduled interview time. If you have issues, please contact support.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(candidateName, roomUrl);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Interview link email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send interview link email to {}: {}", to, e.getMessage());
        }
    }
}
