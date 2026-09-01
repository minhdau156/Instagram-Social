package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.instagram.domain.model.*;
import com.instagram.domain.port.in.*;
import com.instagram.domain.port.out.*;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.instagram.domain.exception.InvalidCredentialsException;
import com.instagram.domain.exception.PasswordResetTokenExpiredException;
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
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
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

    // PASSWORD RESET — REQUEST

    @Test
    void requestPasswordReset_knownEmail_savesTokenAndSendsEmail() {
        // GIVEN
        ReflectionTestUtils.setField(userService, "tokenExpiryMinutes", 30);
        var command = new RequestPasswordResetUseCase.Command("[EMAIL_ADDRESS]");
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

        when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        userService.requestPasswordReset(command);

        // ASSERT
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailPort).sendPasswordResetEmail(eq(command.email()), any());
    }

    @Test
    void requestPasswordReset_unknownEmail_doesNothing() {
        // GIVEN
        var command = new RequestPasswordResetUseCase.Command("[EMAIL_ADDRESS]");
        when(userRepository.findByEmail(command.email())).thenReturn(Optional.empty());

        // ACT
        userService.requestPasswordReset(command);

        // ASSERT
        verifyNoInteractions(passwordResetTokenRepository);
        verifyNoInteractions(emailPort);
    }

    // PASSWORD RESET — CONFIRM

    @Test
    void confirmPasswordReset_validToken_updatesPasswordAndMarksUsed() {
        // GIVEN
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var command = new ConfirmPasswordResetUseCase.Command("raw-token", "newPassword123");
        var token = new PasswordResetToken(tokenId, userId, "irrelevant-hash",
                OffsetDateTime.now().plusMinutes(30), null, OffsetDateTime.now());
        var user = User.builder()
                .id(userId)
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("oldHash")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build();

        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHashPort.hash(command.newPassword())).thenReturn("newHash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        userService.confirmPasswordReset(command);

        // ASSERT
        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCaptor.capture());
        assertEquals("newHash", savedUserCaptor.getValue().getPasswordHash());
        verify(passwordResetTokenRepository).markUsed(tokenId);
    }

    @Test
    void confirmPasswordReset_unknownToken_throws() {
        // GIVEN
        var command = new ConfirmPasswordResetUseCase.Command("raw-token", "newPassword123");
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        // ASSERT
        assertThrows(PasswordResetTokenExpiredException.class, () -> userService.confirmPasswordReset(command));
    }

    @Test
    void confirmPasswordReset_usedToken_throws() {
        // GIVEN
        var command = new ConfirmPasswordResetUseCase.Command("raw-token", "newPassword123");
        var token = new PasswordResetToken(UUID.randomUUID(), UUID.randomUUID(), "irrelevant-hash",
                OffsetDateTime.now().plusMinutes(30), OffsetDateTime.now(), OffsetDateTime.now());
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        // ASSERT
        assertThrows(PasswordResetTokenExpiredException.class, () -> userService.confirmPasswordReset(command));
    }

    @Test
    void confirmPasswordReset_expiredToken_throws() {
        // GIVEN
        var command = new ConfirmPasswordResetUseCase.Command("raw-token", "newPassword123");
        var token = new PasswordResetToken(UUID.randomUUID(), UUID.randomUUID(), "irrelevant-hash",
                OffsetDateTime.now().minusMinutes(1), null, OffsetDateTime.now().minusMinutes(31));
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        // ASSERT
        assertThrows(PasswordResetTokenExpiredException.class, () -> userService.confirmPasswordReset(command));
    }

}
