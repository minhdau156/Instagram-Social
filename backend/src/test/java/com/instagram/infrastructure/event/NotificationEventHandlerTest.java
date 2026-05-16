package com.instagram.infrastructure.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.instagram.domain.event.NotificationEvent;
import com.instagram.domain.model.Notification;
import com.instagram.domain.model.Notification.NotificationType;
import com.instagram.domain.port.in.notification.CreateNotificationUseCase;

@ExtendWith(MockitoExtension.class)
public class NotificationEventHandlerTest {
    @Mock
    private CreateNotificationUseCase createNotificationUseCase;

    @InjectMocks
    private NotificationEventHandler notificationEventHandler;

    @Captor
    private ArgumentCaptor<CreateNotificationUseCase.Command> commandCaptor;

    @Test
    void onNotificationEvent_shouldCreateNotification_whenEventIsLiked() {
        UUID recipientId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        NotificationEvent event = new NotificationEvent(this, Notification.NotificationType.LIKE_POST,
                recipientId, actorId,
                Notification.EntityType.POST, entityId);
        notificationEventHandler.onNotificationEvent(event);
        verify(createNotificationUseCase).createNotification(any(CreateNotificationUseCase.Command.class));
        verify(createNotificationUseCase).createNotification(commandCaptor.capture());
        assertEquals(event.getRecipientId(), commandCaptor.getValue().recipientId());
        assertEquals(event.getActorId(), commandCaptor.getValue().actorId());
        assertEquals(event.getEntityType(), commandCaptor.getValue().entityType());
        assertEquals(event.getEntityId(), commandCaptor.getValue().entityId());
        assertEquals(event.getType(), commandCaptor.getValue().type());
    }

}
