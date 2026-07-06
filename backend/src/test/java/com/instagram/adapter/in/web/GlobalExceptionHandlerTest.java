package com.instagram.adapter.in.web;

import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.domain.exception.*;

import com.instagram.domain.model.RoleName;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleEntityNotFound_Returns404() {
        EntityNotFoundException ex = new EntityNotFoundException("Entity missing");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleEntityNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Entity missing", response.getBody().error());
    }

    @Test
    void handleValidation_Returns400WithErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "field", "must not be blank"));

        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("must not be blank", response.getBody().error());
    }

    @Test
    void handleConstraintViolation_Returns400() {
        ConstraintViolationException ex = new ConstraintViolationException("Violated", null);
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation failed", response.getBody().error());
    }

    @Test
    void handleHttpMessageNotReadable_Returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Malformed JSON", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleHttpMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Malformed request body", response.getBody().error());
    }

    @Test
    void handleException_Returns500() {
        Exception ex = new Exception("Super bad error");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("An unexpected error occurred", response.getBody().error());
    }

    @Test
    void handleUserNotFound_Returns404() {
        UserNotFoundException ex = new UserNotFoundException("User not found");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleUserNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User not found", response.getBody().error());
    }

    @Test
    void handleUserAlreadyExists_Returns409() {
        UserAlreadyExistsException ex = new UserAlreadyExistsException("username", "test");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleUserAlreadyExists(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("A user already exists with username: test", response.getBody().error());
    }

    @Test
    void handleInvalidCredentials_Returns401() {
        InvalidCredentialsException ex = new InvalidCredentialsException("Invalid credentials");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleInvalidCredentials(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid credentials", response.getBody().error());
    }

    @Test
    void handlePasswordResetTokenExpired_Returns400() {
        PasswordResetTokenExpiredException ex = new PasswordResetTokenExpiredException();
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handlePasswordResetTokenExpired(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Password reset token has expired or is invalid", response.getBody().error());
    }

    @Test
    void handleAlreadyFollowing_Returns409() {
        AlreadyFollowingException ex = new AlreadyFollowingException();
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAlreadyFollowing(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Already following or requested to follow user", response.getBody().error());
    }

    @Test
    void handleFollowRequestNotFound_Returns404() {
        FollowRequestNotFoundException ex = new FollowRequestNotFoundException(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleFollowRequestNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Follow request not found: 123e4567-e89b-12d3-a456-426614174000", response.getBody().error());
    }

    @Test
    void handleCannotFollowYourself_Returns400() {
        CannotFollowYourselfException ex = new CannotFollowYourselfException();
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleCannotFollowYourself(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("You cannot follow yourself.", response.getBody().error());
    }

    @Test
    void handleAlreadyLiked_Returns409() {
        AlreadyLikedException ex = new AlreadyLikedException("comment",
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAlreadyLiked(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User has already liked comment '123e4567-e89b-12d3-a456-426614174000'",
                response.getBody().error());
    }

    @Test
    void handleNotLiked_Returns404() {
        NotLikedException ex = new NotLikedException("comment",
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleNotLiked(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User has not liked comment '123e4567-e89b-12d3-a456-426614174000'",
                response.getBody().error());
    }

    @Test
    void handlePostNotFound_returns404() {
        PostNotFoundException ex = new PostNotFoundException(UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handlePostNotFound(ex);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleMediaUpload_returns500() {
        MediaUploadException ex = new MediaUploadException("Can not upload media");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleMediaUpload(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleCommentNotFound_returns404() {
        CommentNotFoundException ex = new CommentNotFoundException(UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleCommentNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleNotSaved_returns409() {
        NotSavedException ex = new NotSavedException(UUID.randomUUID(), UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleNotSaved(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleConversationNotFound_returns404() {
        ConversationNotFoundException ex = new ConversationNotFoundException(UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleConversationNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleMessageNotFound_returns404() {
        MessageNotFoundException ex = new MessageNotFoundException(UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleMessageNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleNotificationNotFound_returns404() {
        NotificationNotFoundException ex = new NotificationNotFoundException(UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleNotificationNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleUnauthorizedNotificationAccess_returns403() {
        UnauthorizedNotificationAccessException ex = new UnauthorizedNotificationAccessException(UUID.randomUUID(), UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleUnauthorizedNotificationAccess(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleAlreadyBlocked_returns409() {
        AlreadyBlockedException ex = new AlreadyBlockedException(UUID.randomUUID(), UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAlreadyBlocked(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleNotBlocked_returns404() {
        NotBlockedException ex = new NotBlockedException(UUID.randomUUID(), UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleNotBlocked(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleSelfBlock_returns404() {
        SelfBlockException ex = new SelfBlockException(UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleSelfBlock(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleUnauthorizedModerationAccess_returns403() {
        UnauthorizedModerationAccessException ex = new UnauthorizedModerationAccessException(UUID.randomUUID(), "moderation");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleUnauthorizedModerationAccess(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleReportNotFound_returns404() {
        ReportNotFoundException ex = new ReportNotFoundException(UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleReportNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleAlreadyReported_returns409() {
        AlreadyReportedException ex = new AlreadyReportedException(UUID.randomUUID(), UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAlreadyReported(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleRoleNotFound_returns404() {
        RoleNotFoundException ex = new RoleNotFoundException(RoleName.ADMIN);
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleRoleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

    }

    @Test
    void handleRoleAlreadyAssigned_returns409() {
        RoleAlreadyAssignedException ex = new RoleAlreadyAssignedException(UUID.randomUUID(), RoleName.USER);
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleRoleAlreadyAssigned(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleRoleNotAssigned_returns404() {
        RoleNotAssignedException ex = new RoleNotAssignedException(UUID.randomUUID(), RoleName.USER);
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleRoleNotAssigned(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void handleProtectedRole_returns409() {
        ProtectedRoleException ex = new ProtectedRoleException(RoleName.USER);
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleProtectedRole(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleInvalidMedia_returns400() {
        InvalidMediaException ex = new InvalidMediaException("Invalid Media");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleInvalidMedia(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
