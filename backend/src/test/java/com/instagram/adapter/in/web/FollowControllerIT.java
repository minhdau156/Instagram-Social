package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.instagram.domain.exception.AlreadyFollowingException;
import com.instagram.domain.exception.CannotFollowYourselfException;
import com.instagram.domain.exception.FollowRequestNotFoundException;
import com.instagram.domain.model.Follow;
import com.instagram.domain.model.FollowStatus;
import com.instagram.domain.model.UserSummary;
import com.instagram.domain.port.in.follow.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
@TestPropertySource(properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class FollowControllerIT {

        @Autowired
        MockMvc mockMvc;

        @MockBean
        FollowUserUseCase followUserUseCase;
        @MockBean
        UnfollowUserUseCase unfollowUserUseCase;
        @MockBean
        GetFollowersUseCase getFollowersUseCase;
        @MockBean
        GetFollowingUseCase getFollowingUseCase;
        @MockBean
        GetFollowRequestsUseCase getFollowRequestsUseCase;
        @MockBean
        ApproveFollowRequestUseCase approveFollowRequestUseCase;
        @MockBean
        DeclineFollowRequestUseCase declineFollowRequestUseCase;

        static final String FOLLOWER_ID = "123e4567-e89b-12d3-a456-426614174000";
        static final String FOLLOWING_ID = "123e4567-e89b-12d3-a456-426614174001";

        // ── follow ────────────────────────────────────────────────────────────────

        @Test
        @WithMockUser(username = FOLLOWER_ID)
        void followUser_whenUserFollowed_returns201Created() throws Exception {
                Follow follow = Follow.builder()
                                .id(UUID.randomUUID())
                                .followerId(UUID.fromString(FOLLOWER_ID))
                                .followingId(UUID.fromString(FOLLOWING_ID))
                                .status(FollowStatus.ACCEPTED)
                                .build();
                when(followUserUseCase.follow(any(FollowUserUseCase.Command.class))).thenReturn(follow);

                // Controller returns ApiResponse<FollowResponse> — payload lives under $.data
                mockMvc.perform(post("/api/v1/users/{username}/follow", "minh")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.followerId").value(FOLLOWER_ID))
                                .andExpect(jsonPath("$.data.followingId").value(FOLLOWING_ID))
                                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        }

        @Test
        @WithMockUser(username = FOLLOWER_ID)
        void unfollowUser_whenUserUnfollowed_returns204NoContent() throws Exception {
                doNothing().when(unfollowUserUseCase).unfollow(any(UnfollowUserUseCase.Command.class));
                mockMvc.perform(delete("/api/v1/users/{username}/follow", "minh")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isNoContent());
        }

        @Test
        void followUser_whenNotAuthenticated_redirectsToOAuth2Login() throws Exception {
                // The app uses OAuth2 login: unauthenticated requests receive a 302 redirect
                // to the OAuth2 authorization endpoint rather than a 401 JSON response.
                mockMvc.perform(post("/api/v1/users/{username}/follow", "minh")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = FOLLOWER_ID)
        void followUser_whenAlreadyFollowing_returns409Conflict() throws Exception {
                when(followUserUseCase.follow(any(FollowUserUseCase.Command.class)))
                                .thenThrow(new AlreadyFollowingException());

                mockMvc.perform(post("/api/v1/users/{username}/follow", "minh")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isConflict());
        }

        @Test
        @WithMockUser(username = FOLLOWER_ID)
        void followUser_whenFollowingSelf_returns400BadRequest() throws Exception {
                when(followUserUseCase.follow(any(FollowUserUseCase.Command.class)))
                                .thenThrow(new CannotFollowYourselfException());

                mockMvc.perform(post("/api/v1/users/{username}/follow", "minh")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = FOLLOWER_ID)
        void unfollowUser_whenFollowNotFound_returns404NotFound() throws Exception {
                org.mockito.Mockito.doThrow(new FollowRequestNotFoundException(UUID.fromString(FOLLOWER_ID)))
                                .when(unfollowUserUseCase).unfollow(any(UnfollowUserUseCase.Command.class));

                mockMvc.perform(delete("/api/v1/users/{username}/follow", "minh")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isNotFound());
        }

        // ── getFollowers ────────────────────────────────────────────────────────

        @Test
        @WithMockUser(username = FOLLOWER_ID)
        void getFollowers_whenUserHasFollowers_returns200OkWithFollowerSummaries() throws Exception {
                UserSummary follower = new UserSummary(
                                UUID.randomUUID(),
                                "minh",
                                "Minh",
                                "profile.jpg",
                                false,
                                false,
                                FollowStatus.ACCEPTED);
                when(getFollowersUseCase.getFollowers(any(GetFollowersUseCase.Query.class)))
                                .thenReturn(java.util.List.of(follower));

                mockMvc.perform(get("/api/v1/users/{username}/followers", "minh")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].username").value("minh"));
        }

        @Test
        @WithMockUser(username = FOLLOWER_ID)
        void getFollowing_whenUserHasFollowing_returns200OkWithFollowingSummaries() throws Exception {
                UserSummary following = new UserSummary(
                                UUID.randomUUID(),
                                "minh",
                                "Minh",
                                "profile.jpg",
                                false,
                                false,
                                FollowStatus.ACCEPTED);
                when(getFollowingUseCase.getFollowing(any(GetFollowingUseCase.Query.class)))
                                .thenReturn(java.util.List.of(following));

                mockMvc.perform(get("/api/v1/users/{username}/following", "minh")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].username").value("minh"));
        }

        @Test
        @WithMockUser(username = FOLLOWING_ID)
        void getFollowRequests_whenUserHasRequests_returns200OkWithFollowRequestSummaries() throws Exception {
                UserSummary userSummary = new UserSummary(
                                UUID.fromString(FOLLOWER_ID),
                                "minh",
                                "Minh",
                                "profile.jpg",
                                false,
                                false,
                                FollowStatus.PENDING);
                when(getFollowRequestsUseCase.getFollowRequests(any(GetFollowRequestsUseCase.Query.class)))
                                .thenReturn(java.util.List.of(userSummary));

                mockMvc.perform(get("/api/v1/follow-requests")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].username").value("minh"));
        }

        @Test
        @WithMockUser(username = FOLLOWING_ID)
        void approveFollowRequest_whenFollowRequestApproved_returns200Ok() throws Exception {
                Follow follow = Follow.builder()
                                .id(UUID.randomUUID())
                                .followerId(UUID.fromString(FOLLOWER_ID))
                                .followingId(UUID.fromString(FOLLOWING_ID))
                                .status(FollowStatus.ACCEPTED)
                                .build();
                when(approveFollowRequestUseCase.approve(any(ApproveFollowRequestUseCase.Command.class)))
                                .thenReturn(follow);

                mockMvc.perform(post("/api/v1/follow-requests/{followRequestId}/approve", FOLLOWER_ID)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.followerId").value(FOLLOWER_ID))
                                .andExpect(jsonPath("$.data.followingId").value(FOLLOWING_ID))
                                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        }

        @Test
        @WithMockUser(username = FOLLOWING_ID)
        void declineFollowRequest_whenFollowRequestDeclined_returns200Ok() throws Exception {
                doNothing().when(declineFollowRequestUseCase).decline(any(DeclineFollowRequestUseCase.Command.class));
                mockMvc.perform(delete("/api/v1/follow-requests/{followRequestId}/decline", FOLLOWER_ID)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isNoContent());
        }
}
