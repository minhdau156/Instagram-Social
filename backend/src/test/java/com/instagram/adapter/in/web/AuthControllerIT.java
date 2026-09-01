package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.instagram.domain.exception.InvalidCredentialsException;
import com.instagram.domain.exception.PasswordResetTokenExpiredException;
import com.instagram.domain.exception.UserAlreadyExistsException;
import com.instagram.domain.model.AuthResult;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStatus;
import com.instagram.adapter.in.web.dto.request.PasswordResetConfirmRequest;
import com.instagram.adapter.in.web.dto.request.PasswordResetRequest;
import com.instagram.domain.port.in.ConfirmPasswordResetUseCase;
import com.instagram.domain.port.in.GetUserProfileUseCase;
import com.instagram.domain.port.in.LoginUseCase;
import com.instagram.domain.port.in.LogoutUseCase;
import com.instagram.domain.port.in.RefreshTokenUseCase;
import com.instagram.domain.port.in.RegisterUserUseCase;
import com.instagram.domain.port.in.RequestPasswordResetUseCase;
import com.instagram.domain.port.in.UpdateProfileUseCase;
import com.instagram.infrastructure.security.SecurityConfig;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
public class AuthControllerIT {
	@MockBean
	private RegisterUserUseCase registerUserUseCase;

	@MockBean
	private LoginUseCase loginUseCase;

	@MockBean
	private RefreshTokenUseCase refreshTokenUseCase;

	@MockBean
	private LogoutUseCase logoutUseCase;

	@MockBean
	private RequestPasswordResetUseCase requestPasswordResetUseCase;

	@MockBean
	private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

	@MockBean
	private GetUserProfileUseCase getUserProfileUseCase;

	@MockBean
	private UpdateProfileUseCase updateProfileUseCase;

	@Autowired
	private ObjectMapper objectMapper;


	@Autowired
	private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;


    @MockBean
    private IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;




	@Test
	void register_returns201_onSuccess() throws Exception {
		// Arrange
		RegisterUserUseCase.Command command = new RegisterUserUseCase.Command(
				"testuser",
				"minh@gmail.com",
				"password",
				"TestUser");


		User user = User.builder()
				.id(UUID.randomUUID())
				.username("testuser")
				.email("minh@gmail.com")
				.passwordHash("hashedPassword")
				.fullName("TestUser")
				.status(UserStatus.ACTIVE)
				.privacyLevel(PrivacyLevel.PUBLIC)
				.isVerified(false)
				.build();

		when(registerUserUseCase.register(any(RegisterUserUseCase.Command.class)))
				.thenReturn(user);

		// Act
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(command)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.username").value("testuser"))
				.andExpect(jsonPath("$.data.email").value("minh@gmail.com"))
				.andExpect(jsonPath("$.data.fullName").value("TestUser"));

		// Assert
		verify(registerUserUseCase, times(1)).register(any(RegisterUserUseCase.Command.class));
	}

	@Test
	void register_returns400_whenUsernameBlank() throws Exception {
		// Arrange
		RegisterUserUseCase.Command command = new RegisterUserUseCase.Command(
				"",
				"minh@gmail.com",
				"password",
				"TestUser");

		// Act
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(command)))
				.andExpect(status().isBadRequest());

	}

	@Test
	void register_returns400_whenPasswordTooShort() throws Exception {
		// Arrange
		RegisterUserUseCase.Command command = new RegisterUserUseCase.Command(
				"testuser",
				"minh@gmail.com",
				"123",
				"TestUser");

		// Act
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(command)))
				.andExpect(status().isBadRequest());

	}

	@Test
	void register_returns409_whenUserAlreadyExists() throws Exception {
		// Arrange
		RegisterUserUseCase.Command command = new RegisterUserUseCase.Command(
				"testuser",
				"minh@gmail.com",
				"password",
				"TestUser");

		when(registerUserUseCase.register(any(RegisterUserUseCase.Command.class)))
				.thenThrow(new UserAlreadyExistsException("username", "testuser"));

		// Act
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(command)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("A user already exists with username: testuser"));

		// Assert
		verify(registerUserUseCase, times(1)).register(any(RegisterUserUseCase.Command.class));
	}

	@Test
	void login_returns200_onSuccess() throws Exception {
		// Arrange
		LoginUseCase.Command command = new LoginUseCase.Command(
				"testuser",
				"password");

		AuthResult authResult = new AuthResult(
				"accessToken",
				"refreshToken",
				3600L);

		when(loginUseCase.login(any(LoginUseCase.Command.class)))
				.thenReturn(authResult);

		// Act
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(command)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").value("accessToken"))
				.andExpect(jsonPath("$.data.refreshToken").value("refreshToken"))
				.andExpect(jsonPath("$.data.expiresIn").value(3600L));

		// Assert
		verify(loginUseCase, times(1)).login(any(LoginUseCase.Command.class));
	}

	@Test
	void login_returns401_onValidCredential() throws Exception {
		// Arrange
		LoginUseCase.Command command = new LoginUseCase.Command(
				"testuser",
				"wrongpassword");

		when(loginUseCase.login(any(LoginUseCase.Command.class)))
				.thenThrow(new InvalidCredentialsException());

		// Act
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(command)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Invalid username or password"));

		// Assert
		verify(loginUseCase, times(1)).login(any(LoginUseCase.Command.class));
	}

	@Test
	void refreshToken_returns200_onSuccess() throws Exception {
		// Arrange
		RefreshTokenUseCase.Command command = new RefreshTokenUseCase.Command(
				"refreshToken");

		AuthResult authResult = new AuthResult(
				"accessToken",
				"refreshToken",
				3600L);

		when(refreshTokenUseCase.refreshToken(any(RefreshTokenUseCase.Command.class)))
				.thenReturn(authResult);

		// Act
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(command)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").value("accessToken"))
				.andExpect(jsonPath("$.data.refreshToken").value("refreshToken"))
				.andExpect(jsonPath("$.data.expiresIn").value(3600L));

		// Assert
		verify(refreshTokenUseCase, times(1)).refreshToken(any(RefreshTokenUseCase.Command.class));
	}

	@Test
	void refreshToken_returns401_onInvalidToken() throws Exception {
		// Arrange
		RefreshTokenUseCase.Command command = new RefreshTokenUseCase.Command(
				"invalidRefreshToken");

		when(refreshTokenUseCase.refreshToken(any(RefreshTokenUseCase.Command.class)))
				.thenThrow(new InvalidCredentialsException());

		// Act
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(command)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Invalid username or password"));

		// Assert
		verify(refreshTokenUseCase, times(1)).refreshToken(any(RefreshTokenUseCase.Command.class));
	}

	@Test
	void requestPasswordReset_validEmail_returns200() throws Exception {
		// Arrange
		PasswordResetRequest request = new PasswordResetRequest("minh@gmail.com");

		// Act
		mockMvc.perform(post("/api/v1/auth/password-reset/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk());

		// Assert
		verify(requestPasswordResetUseCase, times(1))
				.requestPasswordReset(any(RequestPasswordResetUseCase.Command.class));
	}

	@Test
	void requestPasswordReset_invalidEmail_returns400() throws Exception {
		// Arrange
		PasswordResetRequest request = new PasswordResetRequest("not-an-email");

		// Act
		mockMvc.perform(post("/api/v1/auth/password-reset/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void confirmPasswordReset_validRequest_returns200() throws Exception {
		// Arrange
		PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("a-valid-token", "NewSecurePass123!");

		// Act
		mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk());

		// Assert
		verify(confirmPasswordResetUseCase, times(1))
				.confirmPasswordReset(any(ConfirmPasswordResetUseCase.Command.class));
	}

	@Test
	void confirmPasswordReset_blankToken_returns400() throws Exception {
		// Arrange
		PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("", "NewSecurePass123!");

		// Act
		mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void confirmPasswordReset_shortPassword_returns400() throws Exception {
		// Arrange
		PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("a-valid-token", "short");

		// Act
		mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void confirmPasswordReset_expiredOrInvalidToken_returns400() throws Exception {
		// Arrange
		PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("a-valid-token", "NewSecurePass123!");
		org.mockito.Mockito.doThrow(new PasswordResetTokenExpiredException())
				.when(confirmPasswordResetUseCase)
				.confirmPasswordReset(any(ConfirmPasswordResetUseCase.Command.class));

		// Act
		mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
	}

}
