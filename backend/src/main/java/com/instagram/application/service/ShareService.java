package com.instagram.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.instagram.adapter.in.web.GlobalExceptionHandler;
import com.instagram.domain.model.PostShare;
import com.instagram.domain.model.ShareType;
import com.instagram.domain.port.in.share.SharePostUseCase;
import com.instagram.domain.port.out.ShareRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShareService implements SharePostUseCase {

    private final ShareRepository shareRepository;
    Logger log = LoggerFactory.getLogger(ShareService.class);

    @Override
    public PostShare share(SharePostUseCase.Command command) {
        PostShare share = PostShare.of(
                command.postId(),
                command.sharerId(),
                command.recipientId(),
                command.shareType());

        if (command.shareType() == ShareType.DM) {
            log.info("DM share created: {}", share.getId());
        }

        return shareRepository.save(share);
    }
}
