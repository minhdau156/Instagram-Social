package com.instagram.application.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.instagram.domain.exception.AlreadySavedException;
import com.instagram.domain.exception.NotSavedException;
import com.instagram.domain.model.SavedPost;
import com.instagram.domain.port.in.save.GetSavedPostsUseCase;
import com.instagram.domain.port.in.save.SavePostUseCase;
import com.instagram.domain.port.in.save.UnsavePostUseCase;
import com.instagram.domain.port.out.SavedPostRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SavedPostService implements SavePostUseCase, UnsavePostUseCase, GetSavedPostsUseCase {

    private final SavedPostRepository savedPostRepository;

    @Override
    public List<SavedPost> getSavedPosts(GetSavedPostsUseCase.Query query) {
        Pageable pageable = PageRequest.of(query.page(), query.size());
        Page<SavedPost> page = savedPostRepository.findByUserId(query.userId(), pageable);
        return page.getContent();
    }

    @Override
    public void unsave(UnsavePostUseCase.Command command) {
        if (!savedPostRepository.existsByPostIdAndUserId(command.postId(), command.userId())) {
            throw new NotSavedException(command.postId(), command.userId());
        }
        savedPostRepository.delete(command.postId(), command.userId());
    }

    @Override
    public SavedPost save(SavePostUseCase.Command command) {
        if (savedPostRepository.existsByPostIdAndUserId(command.postId(), command.userId())) {
            throw new AlreadySavedException(command.postId(), command.userId());
        }
        SavedPost savedPost = SavedPost.of(command.postId(), command.userId());
        return savedPostRepository.save(savedPost);
    }

}
