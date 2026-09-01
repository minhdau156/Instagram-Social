package com.instagram.adapter.out.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.instagram.domain.port.out.EmailPort;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SmtpEmailAdapter implements EmailPort {

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.password-reset.token-expiry-minutes:30}")
    private int tokenExpiryMinutes;

    private final JavaMailSender mailSender;

    public SmtpEmailAdapter(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your password");
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        message.setText(
                "You requested a password reset.\n\n" +
                "Click the link below to set a new password (valid for " + tokenExpiryMinutes + " minutes):\n\n" +
                resetLink + "\n\n" +
                "If you did not request this, you can safely ignore this email."
        );

        mailSender.send(message);
        log.info("Password reset email sent to {}", toEmail);
    }

}
