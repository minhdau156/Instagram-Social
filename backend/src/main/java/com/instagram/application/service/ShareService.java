package com.instagram.application.service;

import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import com.instagram.domain.model.PostShare;
import com.instagram.domain.model.ShareType;
import com.instagram.domain.port.in.share.SharePostUseCase;
import com.instagram.domain.port.out.ShareRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShareService implements SharePostUseCase {

    private final ShareRepository shareRepository;
    private final Logger logger;

    @Override
    public PostShare share(SharePostUseCase.Command command) {
        PostShare share = PostShare.of(
                command.postId(),
                command.sharerId(),
                command.recipientId(),
                command.shareType());

        if (command.shareType() == ShareType.DM) {
            logger.info("DM share created: {}", share.getId());
        }

        return shareRepository.save(share);
    }
}
