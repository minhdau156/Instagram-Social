package com.instagram.domain.port.out;

import java.util.UUID;

public interface PushNotificationPort {
    void sendPush(UUID userId, String title, String body);
}
