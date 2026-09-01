package com.instagram.application.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.domain.exception.InvalidCredentialsException;
import com.instagram.domain.exception.PasswordResetTokenExpiredException;
import com.instagram.domain.exception.UserAlreadyExistsException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.AuthResult;
import com.instagram.domain.model.Follow;
import com.instagram.domain.model.FollowStatus;
import com.instagram.domain.model.PasswordResetToken;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserProfile;
import com.instagram.domain.model.UserStats;
import com.instagram.domain.model.UserStatus;
import com.instagram.domain.port.in.ConfirmPasswordResetUseCase;
import com.instagram.domain.port.in.GetUserProfileUseCase;
import com.instagram.domain.port.in.LoginUseCase;
import com.instagram.domain.port.in.LogoutUseCase;
import com.instagram.domain.port.in.RefreshTokenUseCase;
import com.instagram.domain.port.in.RegisterUserUseCase;
import com.instagram.domain.port.in.RequestPasswordResetUseCase;
import com.instagram.domain.port.in.UpdateProfileUseCase;
import com.instagram.domain.port.in.rbac.AssignDefaultRoleUseCase;
import com.instagram.domain.port.in.user.FindAllUserUseCase;
import com.instagram.domain.port.in.user.GetUserStatsUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;
import com.instagram.domain.port.in.user.SearchUsersUseCase;
import com.instagram.domain.port.out.EmailPort;
import com.instagram.domain.port.out.FollowRepository;
import com.instagram.domain.port.out.PasswordHashPort;
import com.instagram.domain.port.out.PasswordResetTokenRepository;
import com.instagram.domain.port.out.TokenPort;
import com.instagram.domain.port.out.UserRepository;
import com.instagram.domain.port.out.UserStatsRepository;

import io.micrometer.core.instrument.Counter;

@Service
public class UserService
        implements GetUserUseCase, RegisterUserUseCase, LoginUseCase, RefreshTokenUseCase, LogoutUseCase,
        RequestPasswordResetUseCase, ConfirmPasswordResetUseCase, GetUserProfileUseCase, UpdateProfileUseCase,
        SearchUsersUseCase, GetUserStatsUseCase, FindAllUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** Role embedded in the access token. Roles are expanded in Phase 2. */
    private static final String DEFAULT_ROLE = "USER";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 64;
    @Value("${app.password-reset.token-expiry-minutes:30}")
    private int tokenExpiryMinutes;

    /** Access token lifetime in seconds (15 minutes). */
    private static final long ACCESS_TOKEN_EXPIRES_IN = 900L;

    private final UserRepository userRepository;
    private final PasswordHashPort passwordHashPort;
    private final TokenPort tokenPort;
    private final EmailPort emailPort;
    private final UserStatsRepository userStatsRepository;
    private final FollowRepository followRepository;
    private final AssignDefaultRoleUseCase assignDefaultRoleUseCase;
    private final Counter usersRegisteredCounter;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public UserService(UserRepository userRepository, PasswordHashPort passwordHashPort,
            TokenPort tokenPort, EmailPort emailPort, UserStatsRepository userStatsRepository,
            FollowRepository followRepository, AssignDefaultRoleUseCase assignDefaultRoleUseCase,
            Counter usersRegisteredCounter, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = userRepository;
        this.passwordHashPort = passwordHashPort;
        this.tokenPort = tokenPort;
        this.emailPort = emailPort;
        this.userStatsRepository = userStatsRepository;
        this.followRepository = followRepository;
        this.assignDefaultRoleUseCase = assignDefaultRoleUseCase;
        this.usersRegisteredCounter = usersRegisteredCounter;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    // ── RegisterUserUseCase ──────────────────────────────────────────────────

    @Override
    public User register(RegisterUserUseCase.Command command) {
        if (userRepository.existsByUsername(command.username())) {
            throw new UserAlreadyExistsException("username", command.username());
        }
        if (userRepository.existsByEmail(command.email())) {
            throw new UserAlreadyExistsException("email", command.email());
        }

        String hashedPassword = passwordHashPort.hash(command.password());

        usersRegisteredCounter.increment();

        User user = User.builder()
                .id(UUID.randomUUID())
                .username(command.username())
                .email(command.email())
                .passwordHash(hashedPassword)
                .fullName(command.fullName())
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build();
        User saved = userRepository.save(user);
        userStatsRepository.create(UserStats.zero(saved.getId()));
        assignDefaultRoleUseCase.assignDefaultRole(new AssignDefaultRoleUseCase.Command(saved.getId()));

        return saved;
    }

    // ── LoginUseCase ─────────────────────────────────────────────────────────

    @Override
    public AuthResult login(LoginUseCase.Command command) {
        // Try email first, then fall back to username (identifier can be either).
        User user = userRepository.findByEmail(command.identifier())
                .or(() -> userRepository.findByUsername(command.identifier()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.withActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordHashPort.verify(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = tokenPort.generateAccessToken(user.getId(), null);
        String refreshToken = tokenPort.generateRefreshToken(user.getId());

        return new AuthResult(accessToken, refreshToken, ACCESS_TOKEN_EXPIRES_IN);
    }

    // ── RefreshTokenUseCase ──────────────────────────────────────────────────

    @Override
    public AuthResult refreshToken(RefreshTokenUseCase.Command command) {
        UUID userId = tokenPort.validateRefreshToken(command.refreshToken())
                .orElseThrow(InvalidCredentialsException::new);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        String accessToken = tokenPort.generateAccessToken(userId, null);
        String refreshToken = tokenPort.generateRefreshToken(userId);

        return new AuthResult(accessToken, refreshToken, ACCESS_TOKEN_EXPIRES_IN);
    }

    // ── LogoutUseCase ────────────────────────────────────────────────────────

    @Override
    public void logout(LogoutUseCase.Command command) {
        // Validate token only to log a warning on tampered/expired tokens.
        // Phase 1 has no session store, so there is nothing to invalidate.
        Optional<UUID> userId = tokenPort.validateRefreshToken(command.refreshToken());
        if (userId.isEmpty()) {
            log.warn("logout() called with an invalid or expired refresh token — ignoring");
        }
        // no-op: token blacklisting deferred to Phase 2
    }

    // ── RequestPasswordResetUseCase ──────────────────────────────────────────

    @Override
    @Transactional
    public void requestPasswordReset(RequestPasswordResetUseCase.Command command) {
        // Silent no-op when email is unknown — prevents user enumeration.
        Optional<User> userOpt = userRepository.findByEmail(command.email());
        if (userOpt.isEmpty()) {
            log.debug("requestPasswordReset() - no account found for email, ignoring silently");
            return;
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String resetToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        OffsetDateTime now = OffsetDateTime.now();
        PasswordResetToken token = new PasswordResetToken(
                    UUID.randomUUID(),
                    userOpt.get().getId(),
                    hashToken(resetToken),
                    now.plus(tokenExpiryMinutes, ChronoUnit.MINUTES),
                    null,
                    now
            );
        passwordResetTokenRepository.save(token);

        emailPort.sendPasswordResetEmail(command.email(), resetToken);
    }

    // ── ConfirmPasswordResetUseCase ──────────────────────────────────────────

    @Override
    @Transactional
    public void confirmPasswordReset(ConfirmPasswordResetUseCase.Command command) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashToken(command.token()))
                .orElseThrow(PasswordResetTokenExpiredException::new);

        if (!token.isValid()) {
            throw new PasswordResetTokenExpiredException();
        }

        User user = userRepository.findById(token.userId())
                .orElseThrow(() -> UserNotFoundException.withId(token.userId()));

        String hashedPassword = passwordHashPort.hash(command.newPassword());
        user = user.withUpdatedPasswordHash(hashedPassword);
        userRepository.save(user);
        passwordResetTokenRepository.markUsed(token.id());
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    // ── GetUserProfileUseCase ────────────────────────────────────────────────

    @Override
    @Cacheable(value = "profile", key = "'profile:' + #query.targetUsername()", condition = "#query.targetUsername() != null")
    public UserProfile getUserProfile(GetUserProfileUseCase.Query query) {

        if (query.targetUsername() == null) {
            User user = userRepository.findById(query.currentUserId())
                    .orElseThrow(() -> UserNotFoundException.withId(query.currentUserId()));
            UserStats stats = userStatsRepository.findByUserId(user.getId())
                    .orElse(UserStats.zero(user.getId()));
            return new UserProfile(user, stats, false, null);
        }

        User user = userRepository.findByUsername(query.targetUsername())
                .orElseThrow(() -> UserNotFoundException.withUsername(query.targetUsername()));

        // Phase 1 stub: real counts and follow-state computed in Phase 3.
        UserStats stats = userStatsRepository.findByUserId(user.getId())
                .orElse(UserStats.zero(user.getId()));
        Optional<Follow> follow = followRepository.findByFollowerIdAndFollowingId(
                query.currentUserId(),
                user.getId());
        if (!follow.isPresent() || follow.get().getStatus() == FollowStatus.PENDING) {
            return new UserProfile(user, stats, false, follow.isPresent() ? follow.get().getStatus() : null);
        }
        return new UserProfile(user, stats, true, FollowStatus.ACCEPTED);
    }

    // ── UpdateProfileUseCase ─────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "profile", key = "'profile:' + @userService.getUserName(#command.userId)")
    public User updateProfile(UpdateProfileUseCase.Command command) {
        User user = userRepository.findById(UUID.fromString(command.userId()))
                .orElseThrow(() -> UserNotFoundException.withId(UUID.fromString(command.userId())));

        User updated = user.withUpdatedProfile(
                command.fullName(),
                command.bio(),
                command.website(),
                command.profilePictureUrl(),
                command.privacyLevel());

        return userRepository.save(updated);
    }

    public String getUserName(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId)).getUsername();
    }

    @Override
    public User getUser(GetUserUseCase.Query query) {
        return userRepository.findById(query.userId())
                .orElseThrow(() -> UserNotFoundException.withId(query.userId()));
    }

    // ── SearchUsersUseCase ───────────────────────────────────────────────────

    @Override
    public List<User> searchUsers(SearchUsersUseCase.Command command) {
        return userRepository.searchByUsername(command.term(), command.limit());
    }

    @Override
    @Cacheable(value = "userStats", key = "'userStats:' + #userId")
    public UserStats getUserStats(UUID userId) {
        return userStatsRepository.findByUserId(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));
    }

    @Override
    public List<User> findAllByIds(Collection<UUID> ids) {
        return userRepository.findAllByIds(ids);
    }

    @Override
    public List<UserStats> findAllUserStatByIds(Collection<UUID> userIds) {
        return userStatsRepository.findAllByIds(userIds);
    }
}
