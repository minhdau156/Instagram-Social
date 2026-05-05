package com.instagram.application.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.instagram.domain.exception.PostNotFoundException;
import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.MediaType;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostMedia;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.port.in.*;
import com.instagram.domain.port.out.HashtagRepository;
import com.instagram.domain.port.out.LikeRepository;
import com.instagram.domain.port.out.MediaStoragePort;
import com.instagram.domain.port.out.PostMediaRepository;
import com.instagram.domain.port.out.PostRepository;
import com.instagram.domain.port.out.SavedPostRepository;

@Service
public class PostService implements
        CreatePostUseCase,
        GetPostUseCase,
        UpdatePostUseCase,
        DeletePostUseCase,
        GetUserPostsUseCase,
        GenerateUploadUrlUseCase {

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final HashtagRepository hashtagRepository;
    private final MediaStoragePort mediaStoragePort;
    private final LikeRepository likeRepository;
    private final SavedPostRepository savedPostRepository;

    public PostService(PostRepository postRepository, PostMediaRepository postMediaRepository,
            HashtagRepository hashtagRepository, MediaStoragePort mediaStoragePort,
            LikeRepository likeRepository, SavedPostRepository savedPostRepository) {
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.hashtagRepository = hashtagRepository;
        this.mediaStoragePort = mediaStoragePort;
        this.likeRepository = likeRepository;
        this.savedPostRepository = savedPostRepository;
    }

    @Override
    public Post createPost(CreatePostUseCase.Command command) {
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .userId(command.userId())
                .caption(command.caption())
                .location(command.location())
                .status(PostStatus.PUBLISHED)
                .viewCount(0L)
                .likeCount(0)
                .commentCount(0)
                .saveCount(0)
                .shareCount(0)
                .likedByCurrentUser(false)
                .savedByCurrentUser(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        Post saved = postRepository.save(post);

        if (command.mediaItems() != null) {
            List<PostMedia> mediaList = command.mediaItems().stream().map(m -> PostMedia.builder()
                    .id(UUID.randomUUID())
                    .postId(saved.getId())
                    .mediaUrl(m.mediaKey())
                    .mediaType(MediaType.valueOf(m.mediaType().toUpperCase()))
                    .width(m.width())
                    .height(m.height())
                    .duration(m.duration() != null ? BigDecimal.valueOf(m.duration()) : null)
                    .fileSizeBytes(m.fileSizeBytes())
                    .sortOrder(m.sortOrder())
                    .createdAt(OffsetDateTime.now())
                    .build()).toList();
            postMediaRepository.saveAll(mediaList);
        }

        processHashtags(command.caption());
        processMentions(command.caption());

        return saved;
    }

    @Override
    public Post getPost(GetPostUseCase.Query query) {
        boolean likedByCurrentUser = likeRepository.hasLikedPost(query.id(), query.currentUserId());
        boolean savedByCurrentUser = savedPostRepository.existsByPostIdAndUserId(query.id(), query.currentUserId());

        Post post = postRepository.findById(query.id())
                .orElseThrow(() -> new PostNotFoundException(query.id()));

        return post.builder()
                .likedByCurrentUser(likedByCurrentUser)
                .savedByCurrentUser(savedByCurrentUser)
                .build();
    }

    @Override
    public List<PostMedia> getPostMedia(UUID postId) {
        return postMediaRepository.findByPostId(postId);
    }

    @Override
    public Post updatePost(UpdatePostUseCase.Command command) {
        Post existing = postRepository.findById(command.id())
                .orElseThrow(() -> new PostNotFoundException(command.id()));

        Post updated = existing.withUpdateCaptionAndLocation(command.caption(), command.location());
        Post saved = postRepository.save(updated);

        processHashtags(command.caption());
        processMentions(command.caption());

        return saved;
    }

    @Override
    public void deletePost(DeletePostUseCase.Command command) {
        Post existing = postRepository.findById(command.id())
                .orElseThrow(() -> new PostNotFoundException(command.id()));

        Post softDeleted = existing.withSoftDelete();
        postRepository.save(softDeleted);
    }

    @Override
    public Page<Post> getUserPosts(GetUserPostsUseCase.Query query) {
        return postRepository.findByUserId(query.targetUserId(), PageRequest.of(query.page(), query.size()));
    }

    @Override
    public GenerateUploadUrlUseCase.UploadUrl generateUploadUrl(GenerateUploadUrlUseCase.Command command) {
        String mediaKey = "users/" + command.userId() + "/posts/" + UUID.randomUUID() + "-" + command.filename();
        String presignedUrl = mediaStoragePort.generatePresignedPutUrl(mediaKey, Duration.ofMinutes(15));
        return new GenerateUploadUrlUseCase.UploadUrl(presignedUrl, mediaKey);
    }

    private void processHashtags(String caption) {
        if (caption == null || caption.isEmpty())
            return;
        Pattern.compile("#(\\w+)").matcher(caption).results()
                .map(r -> r.group(1).toLowerCase())
                .distinct()
                .forEach(tag -> {
                    Hashtag hashtag = hashtagRepository.findOrCreate(tag);
                    hashtagRepository.save(hashtag.withIncrementedCount());
                });
    }

    private void processMentions(String caption) {
        if (caption == null || caption.isEmpty())
            return;
        Pattern.compile("@(\\w+)").matcher(caption).results()
                .map(r -> r.group(1).toLowerCase())
                .distinct()
                .toList(); // For now, we just extract. In future, we would dispatch events or save.
    }
}
