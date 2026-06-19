package com.instagram.application.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

import com.instagram.domain.exception.PostNotFoundException;
import com.instagram.domain.exception.UnauthorizedPostAccessException;
import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.MediaType;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostMedia;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.port.in.*;
import com.instagram.domain.port.in.post.FindAllPostMediaUseCase;
import com.instagram.domain.port.out.HashtagRepository;
import com.instagram.domain.port.out.LikeRepository;
import com.instagram.domain.port.out.MediaStoragePort;
import com.instagram.domain.port.out.PostHashtagRepository;
import com.instagram.domain.port.out.PostMediaRepository;
import com.instagram.domain.port.out.PostRepository;
import com.instagram.domain.port.out.SavedPostRepository;

@Service
@Slf4j
public class PostService implements
        CreatePostUseCase,
        GetPostUseCase,
        UpdatePostUseCase,
        DeletePostUseCase,
        GetUserPostsUseCase,
        GenerateUploadUrlUseCase,
        FindAllPostMediaUseCase {

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final HashtagRepository hashtagRepository;
    private final MediaStoragePort mediaStoragePort;
    private final LikeRepository likeRepository;
    private final SavedPostRepository savedPostRepository;
    private final PostHashtagRepository postHashtagRepository;

    public PostService(PostRepository postRepository, PostMediaRepository postMediaRepository,
            HashtagRepository hashtagRepository, MediaStoragePort mediaStoragePort,
            LikeRepository likeRepository, SavedPostRepository savedPostRepository,
            PostHashtagRepository postHashtagRepository) {
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.hashtagRepository = hashtagRepository;
        this.mediaStoragePort = mediaStoragePort;
        this.likeRepository = likeRepository;
        this.savedPostRepository = savedPostRepository;
        this.postHashtagRepository = postHashtagRepository;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "feed", allEntries = true),
            @CacheEvict(value = "userStats", key = "'userStats:' + #command.userId"),
            @CacheEvict(value = "exploreFeed", allEntries = true),
            @CacheEvict(value = "userPosts", key = "'userPosts:' + #command.userId + ':page1'")
    })

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

        processHashtags(command.caption(), saved.getId());
        processMentions(command.caption());

        return saved;
    }

    @Override
    public Post getPost(GetPostUseCase.Query query) {
        boolean likedByCurrentUser = likeRepository.hasLikedPost(query.id(), query.currentUserId());
        boolean savedByCurrentUser = savedPostRepository.existsByPostIdAndUserId(query.id(), query.currentUserId());

        Post post = postRepository.findById(query.id())
                .orElseThrow(() -> new PostNotFoundException(query.id()));

        return post.copy()
                .likedByCurrentUser(likedByCurrentUser)
                .savedByCurrentUser(savedByCurrentUser)
                .build();
    }

    @Override
    @Cacheable(value = "postMedia", key = "'postMedia:' + #postId")
    public List<PostMedia> getPostMedia(UUID postId) {
        return postMediaRepository.findByPostId(postId);
    }

    @Override
    public Post updatePost(UpdatePostUseCase.Command command) {
        Post existing = postRepository.findById(command.id())
                .orElseThrow(() -> new PostNotFoundException(command.id()));

        if (!existing.getUserId().equals(command.requesterId())) {
            throw new UnauthorizedPostAccessException(existing.getId(), command.requesterId());
        }

        Post updated = existing.withUpdateCaptionAndLocation(command.caption(), command.location());
        Post saved = postRepository.save(updated);

        processHashtags(command.caption(), saved.getId());
        processMentions(command.caption());

        return saved;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "userStats", key = "'userStats:' + #command.userId"),
            @CacheEvict(value = "userPosts", key = "'userPosts:' + #command.userId + ':page1'")
    })
    public void deletePost(DeletePostUseCase.Command command) {
        Post existing = postRepository.findById(command.id())
                .orElseThrow(() -> new PostNotFoundException(command.id()));

        if (!existing.getUserId().equals(command.requesterId())) {
            throw new UnauthorizedPostAccessException(existing.getId(), command.requesterId());
        }

        Post softDeleted = existing.withSoftDelete();
        postRepository.save(softDeleted);
    }

    @Override
    @Cacheable(value = "userPosts", key = "'userPosts:' + #query.targetUserId + ':page1'", condition = "#query.page == 0")
    public Page<Post> getUserPosts(GetUserPostsUseCase.Query query) {
        return postRepository.findByUserId(query.targetUserId(), PageRequest.of(query.page(), query.size()));
    }

    @Override
    public GenerateUploadUrlUseCase.UploadUrl generateUploadUrl(GenerateUploadUrlUseCase.Command command) {
        String extension = extractSafeExtension(command.filename());
        String mediaKey = "media/" + command.userId() + "/" + UUID.randomUUID() + extension;
        String presignedUrl = mediaStoragePort.generatePresignedPutUrl(mediaKey, Duration.ofMinutes(5));
        return new GenerateUploadUrlUseCase.UploadUrl(presignedUrl, mediaKey);
    }

    private void processHashtags(String caption, UUID postId) {
        if (caption == null || caption.isEmpty())
            return;
        Pattern.compile("#(\\w+)").matcher(caption).results()
                .map(r -> r.group(1).toLowerCase())
                .distinct()
                .forEach(tag -> {
                    Hashtag hashtag = hashtagRepository.findOrCreate(tag);
                    hashtagRepository.save(hashtag.withIncrementedCount());
                    postHashtagRepository.save(postId, hashtag.getId());
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

    @Override
    public List<PostMedia> findAllByPostIds(Collection<UUID> postIds) {
        return postMediaRepository.findByPostIds(postIds);
    }

    @Async("mediaExecutor")
    public CompletableFuture<Void> generateThumbnailAsync(String mediaUrl, UUID postId) {
        log.info("Thumbnail generation started for post={}", postId);
        try {
            // TODO: call transcoder (FFmpeg, AWS MediaConvert, etc.)
            Thread.sleep(200);
            log.info("Thumbnail generation completed for post={}", postId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thumbnail generation interrupted for post={}", postId);
        } catch (Exception e) {
            log.error("Thumbnail generation failed for post={}", postId, e);
        }
        return CompletableFuture.completedFuture(null);
    }

    private String extractSafeExtension(String filename) {
        if (filename == null || !filename.contains("."))
            return "";
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        // Only pass through known safe extensions — ignore anything else
        return Set.of(".jpg", ".jpeg", ".png", ".webp", ".mp4").contains(ext) ? ext : "";
    }
}
