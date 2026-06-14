package com.instagram.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.instagram.domain.event.NotificationEvent;
import com.instagram.domain.exception.AlreadyFollowingException;
import com.instagram.domain.exception.CannotFollowYourselfException;
import com.instagram.domain.exception.FollowRequestNotFoundException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.Follow;
import com.instagram.domain.model.FollowStatus;
import com.instagram.domain.model.Notification;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserSummary;
import com.instagram.domain.port.in.follow.*;
import com.instagram.domain.port.out.FollowRepository;
import com.instagram.domain.port.out.UserRepository;
import com.instagram.domain.port.out.UserStatsRepository;
import com.instagram.infrastructure.util.CursorEncoder;

@Service
public class FollowService implements FollowUserUseCase,
                UnfollowUserUseCase,
                ApproveFollowRequestUseCase,
                DeclineFollowRequestUseCase,
                GetFollowRequestsUseCase,
                GetFollowersUseCase,
                GetFollowingUseCase {

        private final FollowRepository followRepository;
        private final UserRepository userRepository;
        private final UserStatsRepository userStatsRepository;
        private final ApplicationEventPublisher eventPublisher;

        public FollowService(FollowRepository followRepository,
                        UserRepository userRepository,
                        UserStatsRepository userStatsRepository,
                        ApplicationEventPublisher eventPublisher) {
                this.followRepository = followRepository;
                this.userRepository = userRepository;
                this.userStatsRepository = userStatsRepository;
                this.eventPublisher = eventPublisher;
        }

        @Override
        @Caching(evict = {
                        @CacheEvict(value = "feed", key = "'feed:' + #command.followerId + ':page1'"),
                        @CacheEvict(value = "userStats", key = "'userStats:' + #command.followerId"),
                        @CacheEvict(value = "userStats", key = "'userStats:' + @followService.getFollowingId(#command.targetUsername)"),
                        @CacheEvict(value = "exploreFeed", key = "'exploreFeed:' + #command.followerId + ':page1'"),
                        @CacheEvict(value = "followings", key = "'followings:' + #command.followerId + ':page1'"),
                        @CacheEvict(value = "followers", key = "'followers:' + @followService.getUserId(#command.targetUsername) + ':page1'"),
                        @CacheEvict(value = "profile", allEntries = true)
        })
        public Follow follow(FollowUserUseCase.Command command) {
                User targetUser = userRepository.findByUsername(command.targetUsername())
                                .orElseThrow(() -> new UserNotFoundException(command.targetUsername()));

                if (command.followerId().equals(targetUser.getId())) {
                        throw new CannotFollowYourselfException();
                }

                if (followRepository.findByFollowerIdAndFollowingId(command.followerId(), targetUser.getId())
                                .isPresent()) {
                        throw new AlreadyFollowingException();
                }

                FollowStatus status = targetUser.getPrivacyLevel() == PrivacyLevel.PRIVATE ? FollowStatus.PENDING
                                : FollowStatus.ACCEPTED;

                Follow follow = Follow.of(command.followerId(), targetUser.getId(), status);
                Follow saved = followRepository.save(follow);

                if (status == FollowStatus.ACCEPTED) {
                        userStatsRepository.incrementFollowerCount(targetUser.getId());
                        userStatsRepository.incrementFollowingCount(command.followerId());
                }

                eventPublisher.publishEvent(new NotificationEvent(
                                this,
                                status == FollowStatus.ACCEPTED ? Notification.NotificationType.FOLLOW
                                                : Notification.NotificationType.FOLLOW_REQUEST,
                                targetUser.getId(),
                                command.followerId(),
                                Notification.EntityType.FOLLOW,
                                null));

                return saved;
        }

        public UUID getFollowingId(String username) {
                return this.userRepository.findByUsername(username).orElseThrow(
                                () -> new UserNotFoundException(username)).getId();
        }

        @Override
        @Caching(evict = {
                        @CacheEvict(value = "feed", key = "'feed:' + #command.followerId + ':page1'"),
                        @CacheEvict(value = "userStats", key = "'userStats:' + #command.followerId"),
                        @CacheEvict(value = "userStats", key = "'userStats:' + @followService.getFollowingId(#command.targetUsername)"),
                        @CacheEvict(value = "exploreFeed", key = "'exploreFeed:' + #command.followerId + ':page1'"),
                        @CacheEvict(value = "followings", key = "'followings:' + #command.followerId + ':page1'"),
                        @CacheEvict(value = "followers", key = "'followers:' + @followService.getUserId(#command.targetUsername) + ':page1'"),
                        @CacheEvict(value = "profile", allEntries = true)

        })
        public void unfollow(UnfollowUserUseCase.Command command) {
                User targetUser = userRepository.findByUsername(command.targetUsername())
                                .orElseThrow(() -> new UserNotFoundException(command.targetUsername()));
                Follow follow = followRepository
                                .findByFollowerIdAndFollowingId(command.followerId(), targetUser.getId())
                                .orElseThrow(() -> new FollowRequestNotFoundException(command.followerId()));
                followRepository.delete(command.followerId(), targetUser.getId());
                if (follow.getStatus() == FollowStatus.ACCEPTED) {
                        userStatsRepository.decrementFollowerCount(targetUser.getId());
                        userStatsRepository.decrementFollowingCount(command.followerId());
                }

        }

        @Override
        @Caching(evict = {
                        @CacheEvict(value = "followers", key = "'followers:' + #command.followingId + ':page1'"),
                        @CacheEvict(value = "followings", key = "'followings:' + #command.followRequestId + ':page1'"),
                        @CacheEvict(value = "userStats", key = "'userStats:' + #command.followingId"),
                        @CacheEvict(value = "userStats", key = "'userStats:' + #command.followRequestId"),
                        @CacheEvict(value = "followRequests", key = "'followRequests:' + #command.followingId")
        })
        public Follow approve(ApproveFollowRequestUseCase.Command command) {
                Follow follow = followRepository
                                .findByFollowerIdAndFollowingId(command.followRequestId(), command.followingId())
                                .orElseThrow(() -> new FollowRequestNotFoundException(command.followRequestId()));
                Follow acceptedFollow = follow.withAccepted();
                followRepository.save(acceptedFollow);
                userStatsRepository.incrementFollowerCount(command.followingId());
                userStatsRepository.incrementFollowingCount(command.followRequestId());
                return acceptedFollow;
        }

        @Override
        @CacheEvict(value = "followRequests", key = "'followRequests:' + #command.followingId")
        public void decline(DeclineFollowRequestUseCase.Command command) {
                Follow follow = followRepository
                                .findByFollowerIdAndFollowingId(command.followRequestId(), command.followingId())
                                .orElseThrow(() -> new FollowRequestNotFoundException(command.followRequestId()));
                followRepository.delete(command.followRequestId(), command.followingId());
        }

        @Override
        @Cacheable(value = "followRequests", key = "'followRequests:' + #query.userId")
        public List<UserSummary> getFollowRequests(GetFollowRequestsUseCase.Query query) {
                List<Follow> pendingFollows = followRepository.findPendingRequestsByFollowingId(query.userId());
                Set<UUID> followerIds = pendingFollows.stream()
                                .map(Follow::getFollowerId)
                                .collect(Collectors.toSet());
                Map<UUID, User> userById = userRepository.findAllByIds(followerIds).stream()
                                .collect(Collectors.toMap(User::getId, Function.identity()));

                // 1 query: all people the current user already follows — for isFollowing flag

                return pendingFollows.stream().map(follow -> {
                        User user = userById.get(follow.getFollowerId());
                        return new UserSummary(
                                        user.getId(),
                                        user.getUsername(),
                                        user.getFullName(),
                                        user.getProfilePictureUrl(),
                                        user.isVerified(),
                                        user.getPrivacyLevel() == PrivacyLevel.PRIVATE,
                                        FollowStatus.PENDING);
                }).toList();
        }

        public UUID getUserId(String targetName) {
                return this.userRepository.findByUsername(targetName)
                                .orElseThrow(() -> new UserNotFoundException(targetName)).getId();
        }

        @Override
        @Cacheable(value = "followers", key = "'followers:' + @followService.getUserId(#query.targetUsername) + ':page1'", condition = "#query.cursor() == null")
        public GetFollowersUseCase.FollowersPage getFollowers(GetFollowersUseCase.Query query) {
                CursorEncoder.DecodedCursor decoded = query.cursor() != null
                                ? CursorEncoder.decode(query.cursor())
                                : null;
                String cursorTs = decoded != null ? decoded.createdAt().toString() : null;
                UUID cursorId = decoded != null ? decoded.id() : null;

                Optional<User> targetUser = userRepository.findByUsername(query.targetUsername());
                if (targetUser.isEmpty()) {
                        throw new UserNotFoundException(query.targetUsername());
                }
                UUID targetUserId = targetUser.get().getId();

                // 1 query: all accepted followers of the target user (keyset)
                List<Follow> follows = followRepository.findFollowersByUserIdKeyset(targetUserId, cursorTs, cursorId,
                                query.size());
                if (follows.isEmpty())
                        return new GetFollowersUseCase.FollowersPage(List.of(), null);

                // 1 query: batch-load all follower User objects — no N+1
                Set<UUID> followerIds = follows.stream()
                                .map(Follow::getFollowerId)
                                .collect(Collectors.toSet());
                Map<UUID, User> userById = userRepository.findAllByIds(followerIds).stream()
                                .collect(Collectors.toMap(User::getId, Function.identity()));

                // 1 query: all people the current user already follows — for isFollowing flag
                List<Follow> currentUserFollowing = followRepository.findFollowingByUserId(
                                query.currentUserId(), Pageable.unpaged());
                Map<UUID, FollowStatus> followingStatusById = currentUserFollowing.stream()
                                .collect(Collectors.toMap(Follow::getFollowingId, Follow::getStatus));

                List<UserSummary> items = follows.stream().map(follow -> {
                        User user = userById.get(follow.getFollowerId());
                        FollowStatus status = followingStatusById.getOrDefault(user.getId(), null);
                        return new UserSummary(
                                        user.getId(),
                                        user.getUsername(),
                                        user.getFullName(),
                                        user.getProfilePictureUrl(),
                                        user.isVerified(),
                                        user.getPrivacyLevel() == PrivacyLevel.PRIVATE,
                                        status);
                }).toList();

                // cursor from the last follow: (created_at, follower_id)
                String nextCursor = follows.size() < query.size() ? null
                                : CursorEncoder.encode(follows.getLast().getCreatedAt(),
                                                follows.getLast().getFollowerId());

                return new GetFollowersUseCase.FollowersPage(items, nextCursor);
        }

        @Override
        @Cacheable(value = "followings", key = "'followings:' + @followService.getUserId(#query.targetUsername) + ':page1'", condition = "#query.cursor() == null")
        public GetFollowingUseCase.FollowingPage getFollowing(GetFollowingUseCase.Query query) {
                CursorEncoder.DecodedCursor decoded = query.cursor() != null
                                ? CursorEncoder.decode(query.cursor())
                                : null;
                String cursorTs = decoded != null ? decoded.createdAt().toString() : null;
                UUID cursorId = decoded != null ? decoded.id() : null;

                Optional<User> targetUser = userRepository.findByUsername(query.targetUsername());
                if (targetUser.isEmpty()) {
                        throw new UserNotFoundException(query.targetUsername());
                }
                UUID targetUserId = targetUser.get().getId();

                // 1 query: all accepted follows made by the target user (keyset)
                List<Follow> follows = followRepository.findFollowingByUserIdKeyset(targetUserId, cursorTs, cursorId,
                                query.size());
                if (follows.isEmpty())
                        return new GetFollowingUseCase.FollowingPage(List.of(), null);

                // 1 query: batch-load all followed User objects — no N+1
                Set<UUID> followingIds = follows.stream()
                                .map(Follow::getFollowingId)
                                .collect(Collectors.toSet());
                Map<UUID, User> userById = userRepository.findAllByIds(followingIds).stream()
                                .collect(Collectors.toMap(User::getId, Function.identity()));

                List<UserSummary> items = follows.stream().map(follow -> {
                        User user = userById.get(follow.getFollowingId());
                        return new UserSummary(
                                        user.getId(),
                                        user.getUsername(),
                                        user.getFullName(),
                                        user.getProfilePictureUrl(),
                                        user.isVerified(),
                                        user.getPrivacyLevel() == PrivacyLevel.PRIVATE,
                                        FollowStatus.ACCEPTED);
                }).toList();

                // cursor from the last follow: (created_at, following_id)
                String nextCursor = follows.size() < query.size() ? null
                                : CursorEncoder.encode(follows.getLast().getCreatedAt(),
                                                follows.getLast().getFollowingId());

                return new GetFollowingUseCase.FollowingPage(items, nextCursor);
        }

}
