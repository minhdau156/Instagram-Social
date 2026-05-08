package com.instagram.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.domain.exception.AlreadyLikedException;
import com.instagram.domain.exception.NotLikedException;
import com.instagram.domain.model.Follow;
import com.instagram.domain.model.FollowStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserSummary;
import com.instagram.domain.port.in.like.GetPostLikersUseCase;
import com.instagram.domain.port.in.like.LikeCommentUseCase;
import com.instagram.domain.port.in.like.LikePostUseCase;
import com.instagram.domain.port.in.like.UnlikeCommentUseCase;
import com.instagram.domain.port.in.like.UnlikePostUseCase;
import com.instagram.domain.port.out.CommentRepository;
import com.instagram.domain.port.out.FollowRepository;
import com.instagram.domain.port.out.LikeRepository;
import com.instagram.domain.port.out.UserRepository;
import com.instagram.domain.port.out.PostRepository;

@Service
public class LikeService implements LikePostUseCase,
        UnlikePostUseCase,
        LikeCommentUseCase,
        UnlikeCommentUseCase,
        GetPostLikersUseCase {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    public LikeService(LikeRepository likeRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            UserRepository userRepository,
            FollowRepository followRepository) {
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
    }

    @Override
    @Transactional
    public void like(LikePostUseCase.Command command) {
        if (likeRepository.hasLikedPost(command.postId(), command.userId())) {
            throw new AlreadyLikedException("post", command.postId());
        }
        likeRepository.likePost(command.postId(), command.userId());
    }

    @Override
    @Transactional
    public void unlike(UnlikePostUseCase.Command command) {
        if (!likeRepository.hasLikedPost(command.postId(), command.userId())) {
            throw new NotLikedException("post", command.postId());
        }
        likeRepository.unlikePost(command.postId(), command.userId());
    }

    @Override
    @Transactional
    public void likeComment(LikeCommentUseCase.Command command) {
        if (likeRepository.hasLikedComment(command.commentId(), command.userId())) {
            throw new AlreadyLikedException("comment", command.commentId());
        }
        likeRepository.likeComment(command.commentId(), command.userId());
    }

    @Override
    @Transactional
    public void unlikeComment(UnlikeCommentUseCase.Command command) {
        if (!likeRepository.hasLikedComment(command.commentId(), command.userId())) {
            throw new NotLikedException("comment", command.commentId());
        }
        likeRepository.unlikeComment(command.commentId(), command.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserSummary> getPostLikers(GetPostLikersUseCase.Query query) {
        Pageable pageable = PageRequest.of(query.page(), query.size());

        Page<UUID> postLikerIds = likeRepository.findPostLikerIds(query.postId(), pageable);
        Map<UUID, User> idToUser = userRepository.findAllByIds(postLikerIds.getContent())
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        Page<UserSummary> likers = postLikerIds.map(id -> {
            User user = idToUser.get(id);
            FollowStatus followStatus = null;
            if (query.requestingUserId() != null) {
                Optional<Follow> followOpt = followRepository.findByFollowerIdAndFollowingId(query.requestingUserId(),
                        user.getId());
                if (followOpt.isPresent()) {
                    followStatus = followOpt.get().getStatus();
                }
            }
            return new UserSummary(
                    id,
                    user.getUsername(),
                    user.getFullName(),
                    user.getProfilePictureUrl(),
                    user.isVerified(),
                    user.getPrivacyLevel() == PrivacyLevel.PRIVATE,
                    followStatus);
        });

        return likers;
    }
}
