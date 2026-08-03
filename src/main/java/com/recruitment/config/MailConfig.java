package com.recruitment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.mail.host", havingValue = "smtp.gmail.com")
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        String host = System.getenv("MAIL_HOST");
        mailSender.setHost(host != null && !host.isEmpty() ? host : "smtp.gmail.com");

        String portStr = System.getenv("MAIL_PORT");
        int port = 587;
        if (portStr != null && !portStr.isEmpty()) {
            try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) { }
        }
        mailSender.setPort(port);

        String username = System.getenv("MAIL_USERNAME");
        String password = System.getenv("MAIL_PASSWORD");

        if (username != null && !username.isEmpty()) {
            mailSender.setUsername(username.trim());
            if (password != null) {
                mailSender.setPassword(password.replace(" ", "").trim());
            }
        }

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        return mailSender;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.mail.host", matchIfMissing = false, havingValue = "none")
    public JavaMailSender dummyMailSender() {
        return new JavaMailSenderImpl();
    }
}
