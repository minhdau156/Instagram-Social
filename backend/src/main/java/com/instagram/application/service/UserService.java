package com.instagram.application.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.instagram.domain.exception.InvalidCredentialsException;
import com.instagram.domain.exception.PasswordResetTokenExpiredException;
import com.instagram.domain.exception.UserAlreadyExistsException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.AuthResult;
import com.instagram.domain.model.Follow;
import com.instagram.domain.model.FollowStatus;
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
import com.instagram.domain.port.in.user.FindAllUserUseCase;
import com.instagram.domain.port.in.user.GetUserStatsUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;
import com.instagram.domain.port.in.user.SearchUsersUseCase;
import com.instagram.domain.port.out.EmailPort;
import com.instagram.domain.port.out.FollowRepository;
import com.instagram.domain.port.out.PasswordHashPort;
import com.instagram.domain.port.out.TokenPort;
import com.instagram.domain.port.out.UserRepository;
import com.instagram.domain.port.out.UserStatsRepository;

@Service
public class UserService
        implements GetUserUseCase, RegisterUserUseCase, LoginUseCase, RefreshTokenUseCase, LogoutUseCase,
        RequestPasswordResetUseCase, ConfirmPasswordResetUseCase, GetUserProfileUseCase, UpdateProfileUseCase,
        SearchUsersUseCase, GetUserStatsUseCase, FindAllUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** Role embedded in the access token. Roles are expanded in Phase 2. */
    private static final String DEFAULT_ROLE = "USER";

    /** Access token lifetime in seconds (15 minutes). */
    private static final long ACCESS_TOKEN_EXPIRES_IN = 900L;

    private final UserRepository userRepository;
    private final PasswordHashPort passwordHashPort;
    private final TokenPort tokenPort;
    private final EmailPort emailPort;
    private final UserStatsRepository userStatsRepository;
    private final FollowRepository followRepository;

    public UserService(UserRepository userRepository, PasswordHashPort passwordHashPort,
            TokenPort tokenPort, EmailPort emailPort, UserStatsRepository userStatsRepository,
            FollowRepository followRepository) {
        this.userRepository = userRepository;
        this.passwordHashPort = passwordHashPort;
        this.tokenPort = tokenPort;
        this.emailPort = emailPort;
        this.userStatsRepository = userStatsRepository;
        this.followRepository = followRepository;
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

        return saved;
    }

    // ── LoginUseCase ─────────────────────────────────────────────────────────

    @Override
    public AuthResult login(LoginUseCase.Command command) {
        // Try email first, then fall back to username (identifier can be either).
        User user = userRepository.findByEmail(command.identifier())
                .or(() -> userRepository.findByUsername(command.identifier()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordHashPort.verify(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = tokenPort.generateAccessToken(user.getId(), user.getRole().name());
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

        String accessToken = tokenPort.generateAccessToken(userId, user.getRole().name());
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
    public void requestPasswordReset(RequestPasswordResetUseCase.Command command) {
        // Silent no-op when email is unknown — prevents user enumeration.
        Optional<User> userOpt = userRepository.findByEmail(command.email());
        if (userOpt.isEmpty()) {
            log.debug("requestPasswordReset() - no account found for email, ignoring silently");
            return;
        }

        String resetToken = UUID.randomUUID().toString();

        // TODO (TASK-1.12): persist resetToken in password_reset_tokens table via
        // PasswordResetTokenRepository. Stubbed here to unblock domain wiring.
        log.info("requestPasswordReset() - reset token generated for userId={} [stub: not persisted]",
                userOpt.get().getId());

        emailPort.sendPasswordResetEmail(command.email(), resetToken);
    }

    // ── ConfirmPasswordResetUseCase ──────────────────────────────────────────

    @Override
    public void confirmPasswordReset(ConfirmPasswordResetUseCase.Command command) {
        // TODO (TASK-1.12): load token record from password_reset_tokens via
        // PasswordResetTokenRepository. Stub: no persistence in Phase 1, so any
        // token presented here is treated as not found / expired.
        log.warn("confirmPasswordReset() - PasswordResetTokenRepository not yet implemented; " +
                "throwing PasswordResetTokenExpiredException as stub behaviour");
        throw new PasswordResetTokenExpiredException();

        /*
         * ── Replace the stub above with this block once TASK-1.12 is complete ──
         *
         * PasswordResetToken tokenRecord = passwordResetTokenRepository
         * .findByToken(command.token())
         * .orElseThrow(PasswordResetTokenExpiredException::new);
         *
         * if (tokenRecord.isExpired()) {
         * throw new PasswordResetTokenExpiredException();
         * }
         *
         * User user = userRepository.findById(tokenRecord.userId())
         * .orElseThrow(() -> UserNotFoundException.withId(tokenRecord.userId()));
         *
         * String newHash = passwordHashPort.hash(command.newPassword());
         * User updated = user.withUpdatedPasswordHash(newHash);
         * userRepository.save(updated);
         *
         * // Invalidate the used token so it cannot be replayed.
         * passwordResetTokenRepository.delete(tokenRecord.token());
         * ── end replacement block ──
         */
    }

    // ── GetUserProfileUseCase ────────────────────────────────────────────────

    @Override
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
