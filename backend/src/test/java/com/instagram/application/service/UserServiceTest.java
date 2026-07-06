package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.instagram.domain.model.*;
import com.instagram.domain.port.in.*;
import com.instagram.domain.port.out.*;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.instagram.domain.exception.InvalidCredentialsException;
import com.instagram.domain.exception.UserAlreadyExistsException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.port.in.rbac.AssignDefaultRoleUseCase;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHashPort passwordHashPort;
    @Mock
    private TokenPort tokenPort;
    @Mock
    private UserStatsRepository userStatsRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private EmailPort emailPort;
    @Mock
    private AssignDefaultRoleUseCase assignRoleUseCase;
    @Mock
    private Counter usersRegisteredCounter;
    @InjectMocks
    private UserService userService;

    // REGISTER

    @Test
    void register_success_returnsUser() {
        // GIVEN
        var command = new RegisterUserUseCase.Command("testuser", "[EMAIL_ADDRESS]", "password", "TestUser");
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build();

        // ACT
        when(userRepository.existsByUsername(command.username())).thenReturn(false);
        when(userRepository.existsByEmail(command.email())).thenReturn(false);
        when(passwordHashPort.hash(command.password())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // ASSERT
        User result = userService.register(command);
        assertEquals(user, result);

        verify(userRepository).save(any(User.class));

    }

    @Test
    void register_usernameExists_throwsUserAlreadyExistsException() {
        // GIVEN
        var command = new RegisterUserUseCase.Command("testuser", "[EMAIL_ADDRESS]", "password", "TestUser");

        // ACT
        when(userRepository.existsByUsername(command.username())).thenReturn(true);

        // ASSERT
        assertThrows(UserAlreadyExistsException.class, () -> userService.register(command));
    }

    @Test
    void register_emailExists_throwsUserAlreadyExistsException() {
        // GIVEN
        var command = new RegisterUserUseCase.Command("testuser", "[EMAIL_ADDRESS]", "password", "TestUser");

        // ACT
        when(userRepository.existsByUsername(command.username())).thenReturn(false);
        when(userRepository.existsByEmail(command.email())).thenReturn(true);

        // ASSERT
        assertThrows(UserAlreadyExistsException.class, () -> userService.register(command));
    }

    // LOGIN

    @Test
    void login_success_returnsAuthResult() {
        // GIVEN
        var command = new LoginUseCase.Command("testuser", "password");
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        when(userRepository.findByUsername(command.identifier())).thenReturn(Optional.of(user));
        when(passwordHashPort.verify(command.password(), user.getPasswordHash())).thenReturn(true);
        when(tokenPort.generateAccessToken(user.getId(), null)).thenReturn("accessToken");
        when(tokenPort.generateRefreshToken(user.getId())).thenReturn("refreshToken");

        // ASSERT
        AuthResult result = userService.login(command);

        assertEquals("accessToken", result.accessToken());
        assertEquals("refreshToken", result.refreshToken());
        assertEquals(900L, result.expiresIn());

    }

    @Test
    void login_throwException_whenUserNotFound() {
        // GIVEN
        var command = new LoginUseCase.Command("testuser", "password");

        when(userRepository.findByUsername(command.identifier())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(command.identifier())).thenReturn(Optional.empty());

        // ASSERT && ACT
        assertThrows(InvalidCredentialsException.class, () -> userService.login(command));
    }

    @Test
    void login_throwException_whenInvalidPassword() {
        // GIVEN
        var command = new LoginUseCase.Command("testuser", "wrongpassword");
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        when(userRepository.findByUsername(command.identifier())).thenReturn(Optional.of(user));
        when(passwordHashPort.verify(command.password(), user.getPasswordHash())).thenReturn(false);

        // ASSERT && ACT
        assertThrows(InvalidCredentialsException.class, () -> userService.login(command));
    }

    @Test
    void login_throwException_whenUserDeactivated() {
        // GIVEN
        var command = new LoginUseCase.Command("testuser", "password");
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.DEACTIVATED)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        when(userRepository.findByUsername(command.identifier())).thenReturn(Optional.of(user));

        // ASSERT && ACT
        assertThrows(InvalidCredentialsException.class, () -> userService.login(command));
    }

    @Test
    void updateProfile_success_returnsSavedUser() {
        // GIVEN
        var command = new UpdateProfileUseCase.Command(UUID.randomUUID().toString(), "newname", "newbio", "newwebsite",
                null, null);
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("newname")
                .bio("newbio")
                .websiteUrl("newwebsite")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        when(userRepository.findById(UUID.fromString(command.userId()))).thenReturn(Optional.of((User) user));
        when(userRepository.save(any(User.class))).thenReturn((User) user);

        // ACT
        User result = userService.updateProfile(command);

        // ASSERT
        assertEquals(user.getFullName(), result.getFullName());
        assertEquals(user.getBio(), result.getBio());
        assertEquals(user.getWebsiteUrl(), result.getWebsiteUrl());

        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateProfile_throwException_whenUserNotFound() {
        // GIVEN
        var command = new UpdateProfileUseCase.Command(UUID.randomUUID().toString(), "newname", "newbio", "newwebsite",
                null, null);
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("newname")
                .bio("newbio")
                .websiteUrl("newwebsite")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        when(userRepository.findById(UUID.fromString(command.userId()))).thenReturn(Optional.empty());

        // ACT
        assertThrows(UserNotFoundException.class, () -> userService.updateProfile(command));

    }

    @Test
    void refreshToken_whenSuccess_returnAuthToken() {
        UUID userId = UUID.randomUUID();
        var user = User.builder()
                .id(userId)
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("newname")
                .bio("newbio")
                .websiteUrl("newwebsite")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();
        when(tokenPort.validateRefreshToken(any())).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenPort.generateAccessToken(userId, null)).thenReturn("accessToken");
        when(tokenPort.generateRefreshToken(userId)).thenReturn("refreshToken");

        RefreshTokenUseCase.Command command = new RefreshTokenUseCase.Command("token");

        AuthResult result = userService.refreshToken(command);

        verify(tokenPort).validateRefreshToken(any());
        verify(tokenPort).generateRefreshToken(any());
    }

    @Test
    void refreshToken_throwException_whenRefreshTokenInvalid() {
        when(tokenPort.validateRefreshToken(any())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> userService.refreshToken(new RefreshTokenUseCase.Command("token")));
    }

    @Test
    void refreshToken_throwException_whenUserNotFound() {
        when(tokenPort.validateRefreshToken(any())).thenReturn(Optional.of(UUID.randomUUID()));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.refreshToken(new RefreshTokenUseCase.Command("token")));
    }


    @Test
    void getUserProfile_isOwn_returnOwnProfile() {
        UUID userId = UUID.randomUUID();
        var user = User.builder()
                .id(userId)
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("newname")
                .bio("newbio")
                .websiteUrl("newwebsite")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.empty());

        GetUserProfileUseCase.Query query = new GetUserProfileUseCase.Query(null, userId);

        UserProfile result = userService.getUserProfile(query);

        assertEquals(0, result.stats().postCount());

    }

    @Test
    void getUserProfile_isOwnWhenUserNotFound_throwException() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserProfile(new GetUserProfileUseCase.Query(null, UUID.randomUUID())));
    }

    @Test
    void getUserProfile_viewOtherProfile_returnOtherProfile() {
        UUID userId = UUID.randomUUID();
        var user = User.builder()
                .id(userId)
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("newname")
                .bio("newbio")
                .websiteUrl("newwebsite")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(user));
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(followRepository.findByFollowerIdAndFollowingId(any(), any())).thenReturn(Optional.empty());

        UserProfile userProfile = userService.getUserProfile(new GetUserProfileUseCase.Query("minh", userId));

        assertEquals(null, userProfile.followStatus());
    }

    @Test
    void getUserProfile_otherProfileNotFound_throwException() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserProfile(new GetUserProfileUseCase.Query("minh", UUID.randomUUID())));
    }


}
