package com.instagram.infrastructure.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.instagram.domain.event.NotificationEvent;
import com.instagram.domain.port.in.notification.CreateNotificationUseCase;

@Component
public class NotificationEventHandler {

    private final CreateNotificationUseCase createNotificationUseCase;

    public NotificationEventHandler(CreateNotificationUseCase createNotificationUseCase) {
        this.createNotificationUseCase = createNotificationUseCase;
    }

    @EventListener
    @Async
    public void onNotificationEvent(NotificationEvent event) {
        CreateNotificationUseCase.Command command = new CreateNotificationUseCase.Command(
                event.getType(),
                event.getRecipientId(),
                event.getActorId(),
                event.getEntityType(),
                event.getEntityId());
        createNotificationUseCase.createNotification(command);
    }

}
