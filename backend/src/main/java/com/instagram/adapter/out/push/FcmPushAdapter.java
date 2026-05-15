package com.instagram.adapter.out.push;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.DeviceTokenJpaEntity;
import com.instagram.adapter.out.persistence.repository.DeviceTokenJpaRepository;
import com.instagram.domain.port.out.PushNotificationPort;

@Component
public class FcmPushAdapter implements PushNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(FcmPushAdapter.class);

    private final DeviceTokenJpaRepository deviceTokenJpaRepository;

    public FcmPushAdapter(DeviceTokenJpaRepository deviceTokenJpaRepository) {
        this.deviceTokenJpaRepository = deviceTokenJpaRepository;
    }

    @Override
    public void sendPush(UUID userId, String title, String body) {
        List<DeviceTokenJpaEntity> tokens = deviceTokenJpaRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            return;
        }
        for (DeviceTokenJpaEntity token : tokens) {
            log.info("FCM push → token={} title='{}' body='{}'", token.getToken(), title, body);
        }
    }
}
