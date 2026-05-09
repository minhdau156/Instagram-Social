package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.instagram.domain.exception.InvalidCredentialsException;
import com.instagram.domain.exception.UserAlreadyExistsException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.AuthResult;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStatus;
import com.instagram.domain.port.in.LoginUseCase;
import com.instagram.domain.port.in.RegisterUserUseCase;
import com.instagram.domain.port.in.UpdateProfileUseCase;
import com.instagram.domain.port.out.EmailPort;
import com.instagram.domain.port.out.PasswordHashPort;
import com.instagram.domain.port.out.TokenPort;
import com.instagram.domain.port.out.UserRepository;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHashPort passwordHashPort;
    @Mock
    private TokenPort tokenPort;
    @Mock
    private EmailPort emailPort;
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
                .build();

        // ACT
        when(userRepository.findByUsername(command.identifier())).thenReturn(Optional.of(user));
        when(passwordHashPort.verify(command.password(), user.getPasswordHash())).thenReturn(true);
        when(tokenPort.generateAccessToken(user.getId(), "USER")).thenReturn("accessToken");
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
                .build();

        when(userRepository.findById(UUID.fromString(command.userId()))).thenReturn(Optional.empty());

        // ACT
        assertThrows(UserNotFoundException.class, () -> userService.updateProfile(command));

    }

}
