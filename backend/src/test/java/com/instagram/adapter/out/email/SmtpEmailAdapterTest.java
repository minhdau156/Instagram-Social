package com.instagram.adapter.out.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpEmailAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SmtpEmailAdapter(mailSender);
        ReflectionTestUtils.setField(adapter, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(adapter, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(adapter, "tokenExpiryMinutes", 30);
    }

    @Test
    void sendPasswordResetEmail_sendsMessageWithResetLink() {
        // Given
        String toEmail = "test@example.com";
        String resetToken = "token123";

        // When
        adapter.sendPasswordResetEmail(toEmail, resetToken);

        // Then
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertEquals("noreply@example.com", message.getFrom());
        assertEquals(toEmail, message.getTo()[0]);
        assertTrue(message.getText().contains("http://localhost:5173/reset-password?token=" + resetToken));
        assertTrue(message.getText().contains("valid for 30 minutes"));
    }
}
