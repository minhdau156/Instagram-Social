package com.instagram.domain.port.in.share;

import java.util.UUID;

import com.instagram.domain.model.PostShare;
import com.instagram.domain.model.ShareType;

public interface SharePostUseCase {
    PostShare share(Command command);

    record Command(
            UUID postId,
            UUID sharerId,
            UUID recipientId,
            ShareType shareType) {
    }
}
