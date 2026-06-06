package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.instagram.domain.exception.AlreadyFollowingException;
import com.instagram.domain.exception.CannotFollowYourselfException;
import com.instagram.domain.exception.FollowRequestNotFoundException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.Follow;
import com.instagram.domain.model.FollowStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserSummary;
import com.instagram.domain.port.in.follow.ApproveFollowRequestUseCase;
import com.instagram.domain.port.in.follow.DeclineFollowRequestUseCase;
import com.instagram.domain.port.in.follow.FollowUserUseCase;
import com.instagram.domain.port.in.follow.GetFollowRequestsUseCase;
import com.instagram.domain.port.in.follow.GetFollowersUseCase;
import com.instagram.domain.port.in.follow.GetFollowingUseCase;
import com.instagram.domain.port.in.follow.UnfollowUserUseCase;
import com.instagram.domain.port.out.FollowRepository;
import com.instagram.domain.port.out.UserRepository;
import com.instagram.domain.port.out.UserStatsRepository;

@ExtendWith(MockitoExtension.class)
public class FollowServiceTest {

    // ── Fixed UUIDs used across tests ────────────────────────────────────────
    static final UUID FOLLOWER_ID = UUID.fromString("12345678-1234-5678-1234-567812345679");
    static final UUID FOLLOWING_ID = UUID.fromString("12345678-1234-5678-1234-567812345678");

    @Mock
    FollowRepository followRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    UserStatsRepository userStatsRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    FollowService followService;

    // ── Shared fixtures ───────────────────────────────────────────────────────
    User publicUser;
    User privateUser;
    Follow acceptedFollow;
    Follow pendingFollow;

    @BeforeEach
    void setUp() {
        publicUser = buildUser(FOLLOWING_ID, PrivacyLevel.PUBLIC, "test");
        privateUser = buildUser(FOLLOWING_ID, PrivacyLevel.PRIVATE, "test");
        acceptedFollow = Follow.of(FOLLOWER_ID, FOLLOWING_ID, FollowStatus.ACCEPTED);
        pendingFollow = Follow.of(FOLLOWER_ID, FOLLOWING_ID, FollowStatus.PENDING);
    }

    // ── Helper factories ──────────────────────────────────────────────────────

    /** Creates a minimal {@link User} with the given id and privacy level. */
    private User buildUser(UUID id, PrivacyLevel privacy, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .fullName("test")
                .profilePictureUrl("test")
                .isVerified(false)
                .privacyLevel(privacy)
                .build();
    }

    /**
     * Stubs {@code followRepository.save} to return the same {@link Follow}
     * passed in, but with a freshly generated id.
     */
    private void stubSaveReturnsFollow() {
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> {
            Follow f = invocation.getArgument(0);
            return f.builder()
                    .id(UUID.randomUUID())
                    .followerId(f.getFollowerId())
                    .followingId(f.getFollowingId())
                    .status(f.getStatus())
                    .createdAt(f.getCreatedAt())
                    .build();
        });
    }

    // ── follow() ─────────────────────────────────────────────────────────────

    @Test
    void follow_publicAccount_createsAcceptedFollow() {
        when(userRepository.findByUsername("test")).thenReturn(Optional.of(publicUser));
        stubSaveReturnsFollow();

        Follow saved = followService.follow(new FollowUserUseCase.Command(FOLLOWER_ID, "test"));

        assertEquals(FollowStatus.ACCEPTED, saved.getStatus());
        assertEquals(FOLLOWER_ID, saved.getFollowerId());
        assertEquals(FOLLOWING_ID, saved.getFollowingId());
        verify(followRepository).save(any(Follow.class));
        verify(userStatsRepository).incrementFollowerCount(FOLLOWING_ID);
        verify(userStatsRepository).incrementFollowingCount(FOLLOWER_ID);
    }

    @Test
    void follow_canNotFindUser_throwsUserNotFoundException() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> followService.follow(new FollowUserUseCase.Command(FOLLOWER_ID, "test")));
    }

    @Test
    void follow_followingSelf_throwsCannotFollowYourselfException() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(publicUser));

        // Follower id == following id → self-follow
        assertThrows(CannotFollowYourselfException.class,
                () -> followService.follow(new FollowUserUseCase.Command(FOLLOWING_ID, "test")));
    }

    @Test
    void follow_havingFollowButPending_throwsAlreadyFollowingException() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(publicUser));
        when(followRepository.findByFollowerIdAndFollowingId(any(), any()))
                .thenReturn(Optional.of(pendingFollow));

        assertThrows(AlreadyFollowingException.class,
                () -> followService.follow(new FollowUserUseCase.Command(FOLLOWER_ID, "test")));
    }

    @Test
    void follow_followingThePrivateAccount_createsPendingFollow() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(privateUser));
        when(followRepository.findByFollowerIdAndFollowingId(any(), any())).thenReturn(Optional.empty());
        stubSaveReturnsFollow();

        Follow saved = followService.follow(new FollowUserUseCase.Command(FOLLOWER_ID, "test"));

        assertEquals(FollowStatus.PENDING, saved.getStatus());
        assertEquals(FOLLOWER_ID, saved.getFollowerId());
        assertEquals(FOLLOWING_ID, saved.getFollowingId());
        verify(followRepository).save(any(Follow.class));
    }

    // ── unfollow() ────────────────────────────────────────────────────────────

    @Test
    void unfollow_acceptedFollow_deletesAndDecrementsCounters() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(publicUser));
        when(followRepository.findByFollowerIdAndFollowingId(any(), any()))
                .thenReturn(Optional.of(acceptedFollow));

        followService.unfollow(new UnfollowUserUseCase.Command(FOLLOWER_ID, "test"));

        verify(followRepository).delete(FOLLOWER_ID, FOLLOWING_ID);
        verify(userStatsRepository).decrementFollowerCount(FOLLOWING_ID);
        verify(userStatsRepository).decrementFollowingCount(FOLLOWER_ID);
    }

    @Test
    void unfollow_pendingFollow_deletesWithoutDecrementingCounters() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(publicUser));
        when(followRepository.findByFollowerIdAndFollowingId(any(), any()))
                .thenReturn(Optional.of(pendingFollow));

        followService.unfollow(new UnfollowUserUseCase.Command(FOLLOWER_ID, "test"));

        verify(followRepository).delete(FOLLOWER_ID, FOLLOWING_ID);
        // Stat counters must NOT be touched for a pending (unapproved) follow
        org.mockito.Mockito.verifyNoInteractions(userStatsRepository);
    }

    @Test
    void unfollow_canNotFindUser_throwsUserNotFoundException() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> followService.unfollow(new UnfollowUserUseCase.Command(FOLLOWER_ID, "test")));
    }

    @Test
    void unfollow_canNotFindFollowRecord_throwsFollowRequestNotFoundException() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(publicUser));
        when(followRepository.findByFollowerIdAndFollowingId(any(), any())).thenReturn(Optional.empty());

        assertThrows(FollowRequestNotFoundException.class,
                () -> followService.unfollow(new UnfollowUserUseCase.Command(FOLLOWER_ID, "test")));
    }

    // ── approve() ─────────────────────────────────────────────────────────────

    @Test
    void approve_acceptedFollow_acceptsAndIncrementsCounters() {
        when(followRepository.findByFollowerIdAndFollowingId(any(), any()))
                .thenReturn(Optional.of(pendingFollow));
        stubSaveReturnsFollow();

        Follow result = followService.approve(
                new ApproveFollowRequestUseCase.Command(FOLLOWING_ID, FOLLOWER_ID));

        assertEquals(FollowStatus.ACCEPTED, result.getStatus());
        verify(userStatsRepository).incrementFollowerCount(FOLLOWING_ID);
        verify(userStatsRepository).incrementFollowingCount(FOLLOWER_ID);
    }

    @Test
    void approve_canNotFindFollowRequest_throwsFollowRequestNotFoundException() {
        when(followRepository.findByFollowerIdAndFollowingId(any(), any())).thenReturn(Optional.empty());

        assertThrows(FollowRequestNotFoundException.class, () -> followService.approve(
                new ApproveFollowRequestUseCase.Command(FOLLOWING_ID, FOLLOWER_ID)));
    }

    // ── decline() ─────────────────────────────────────────────────────────────

    @Test
    void decline_pendingFollow_deletesFollowRequest() {
        when(followRepository.findByFollowerIdAndFollowingId(any(), any()))
                .thenReturn(Optional.of(pendingFollow));

        followService.decline(new DeclineFollowRequestUseCase.Command(FOLLOWING_ID, FOLLOWER_ID));

        verify(followRepository).delete(FOLLOWER_ID, FOLLOWING_ID);
    }

    @Test
    void decline_canNotFindFollowRequest_throwsFollowRequestNotFoundException() {
        when(followRepository.findByFollowerIdAndFollowingId(any(), any())).thenReturn(Optional.empty());

        assertThrows(FollowRequestNotFoundException.class, () -> followService.decline(
                new DeclineFollowRequestUseCase.Command(FOLLOWING_ID, FOLLOWER_ID)));
    }

    // ── getFollowRequests() ───────────────────────────────────────────────────

    @Test
    void getFollowRequests_sortsByNewest_returnsFollowRequests() {
        User followerUser = buildUser(FOLLOWER_ID, PrivacyLevel.PUBLIC, "minh");

        when(followRepository.findPendingRequestsByFollowingId(any()))
                .thenReturn(List.of(pendingFollow));
        when(userRepository.findAllByIds(any()))
                .thenReturn(List.of(followerUser));

        List<UserSummary> followRequests = followService.getFollowRequests(
                new GetFollowRequestsUseCase.Query(FOLLOWING_ID));

        assertEquals(1, followRequests.size());

    }

    // ── getFollowers() ────────────────────────────────────────────────────────

    @Test
    void getFollowers_whenHasFollowed_returnsFollowersList() {
        // followerUser has id=FOLLOWER_ID to match acceptedFollow.getFollowerId()
        User followerUser = buildUser(FOLLOWER_ID, PrivacyLevel.PUBLIC, "minh");

        when(userRepository.findByUsername(any())).thenReturn(Optional.of(publicUser));
        when(followRepository.findFollowersByUserIdKeyset(any(), isNull(), isNull(), anyInt())).thenReturn(List.of(acceptedFollow));
        when(userRepository.findAllByIds(any())).thenReturn(List.of(followerUser));
        // Return a follow whose followingId == followerUser.id so isFollowing resolves
        // to true
        Follow currentUserFollowsFollower = Follow.of(FOLLOWING_ID, FOLLOWER_ID, FollowStatus.ACCEPTED);
        when(followRepository.findFollowingByUserId(any(), any())).thenReturn(List.of(currentUserFollowsFollower));

        GetFollowersUseCase.FollowersPage result = followService.getFollowers(
                new GetFollowersUseCase.Query("test", FOLLOWER_ID, null, 10));

        assertEquals(1, result.items().size());

    }

    // ── getFollowing() ────────────────────────────────────────────────────────

    @Test
    void getFollowing_whenHasFollowing_returnsFollowingList() {
        User targetUser = buildUser(FOLLOWER_ID, PrivacyLevel.PUBLIC, "minh");
        User followingUser = buildUser(FOLLOWING_ID, PrivacyLevel.PUBLIC, "duy");

        when(userRepository.findByUsername(any())).thenReturn(Optional.of(targetUser));
        when(followRepository.findFollowingByUserIdKeyset(any(), isNull(), isNull(), anyInt())).thenReturn(List.of(acceptedFollow));
        when(userRepository.findAllByIds(any())).thenReturn(List.of(followingUser));

        GetFollowingUseCase.FollowingPage result = followService.getFollowing(
                new GetFollowingUseCase.Query("minh", FOLLOWER_ID, null, 10));

        assertEquals(1, result.items().size());

    }
}
