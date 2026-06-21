package com.instagram.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.domain.port.in.RegisterUserUseCase;
import com.instagram.domain.port.in.LoginUseCase;
import com.instagram.domain.port.in.LogoutUseCase;
import com.instagram.domain.port.in.RefreshTokenUseCase;
import com.instagram.domain.port.in.RequestPasswordResetUseCase;

import jakarta.validation.Valid;

import com.instagram.domain.model.User;
import com.instagram.domain.port.in.ConfirmPasswordResetUseCase;
import com.instagram.adapter.in.web.dto.request.LoginRequest;
import com.instagram.adapter.in.web.dto.request.PasswordResetConfirmRequest;
import com.instagram.adapter.in.web.dto.request.PasswordResetRequest;
import com.instagram.adapter.in.web.dto.request.RefreshRequest;
import com.instagram.adapter.in.web.dto.request.RegisterRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.AuthResponse;
import com.instagram.adapter.in.web.dto.response.UserResponse;
import com.instagram.domain.model.AuthResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication and management")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    @Operation(summary = "Register a new user", description = "Registers a new user and returns their profile")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User successfully registered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest req) {
        User user = registerUserUseCase.register(new RegisterUserUseCase.Command(
                req.username(), req.email(), req.password(), req.fullName()));
        log.info("User registered username={}", req.username());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(UserResponse.from(user)));
    }

    @Operation(summary = "User Login", description = "Authenticates a user and returns access and refresh tokens")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully logged in"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        AuthResult authResult = loginUseCase.login(new LoginUseCase.Command(req.identifier(), req.password()));
        log.info("User login identifier={}", req.identifier());
        return ResponseEntity.ok(ApiResponse.ok(AuthResponse.from(authResult)));
    }

    @Operation(summary = "Refresh Token", description = "Generates new access token using a valid refresh token")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token successfully refreshed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or missing token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token expired or revoked")
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest req) {
        AuthResult authResult = refreshTokenUseCase
                .refreshToken(new RefreshTokenUseCase.Command(req.refreshToken()));
        log.debug("Token refresh requested");
        return ResponseEntity.ok(ApiResponse.ok(AuthResponse.from(authResult)));
    }

    @Operation(summary = "Logout user", description = "Revokes the active refresh token")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Successfully logged out"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest req) {
        logoutUseCase.logout(new LogoutUseCase.Command(req.refreshToken()));
        log.info("User logout");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "Request Password Reset", description = "Sends a password reset link to the registered email")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset email sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest req) {
        requestPasswordResetUseCase.requestPasswordReset(new RequestPasswordResetUseCase.Command(req.email()));
        log.info("Password reset requested email={}", req.email());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "Confirm Password Reset", description = "Resets the user password using a valid token")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password successfully reset"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed or token expired")
    })
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req) {
        confirmPasswordResetUseCase
                .confirmPasswordReset(new ConfirmPasswordResetUseCase.Command(req.token(), req.newPassword()));
        log.info("Password reset confirmed");
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

}
