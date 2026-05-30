package com.instagram.adapter.in.web;

import com.instagram.adapter.in.web.dto.request.CreatePostRequest;
import com.instagram.adapter.in.web.dto.request.UpdatePostRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.PagedResponse;
import com.instagram.adapter.in.web.dto.response.PostResponse;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostMedia;
import com.instagram.domain.port.in.*;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.concurrent.TimeUnit;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "CRUD operations for posts")
public class PostController {

	private final CreatePostUseCase createPostUseCase;
	private final GetPostUseCase getPostUseCase;
	private final UpdatePostUseCase updatePostUseCase;
	private final DeletePostUseCase deletePostUseCase;
	private final GetUserPostsUseCase getUserPostsUseCase;

	@PostMapping
	@Operation(summary = "Create a new post with at least one media item")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Post created successfully"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
	})
	public ResponseEntity<ApiResponse<PostResponse>> createPost(
			@Valid @RequestBody CreatePostRequest request,
			@AuthenticationPrincipal UserDetails userDetails) {

		UUID effectiveUserId = UUID.fromString(userDetails.getUsername());

		Post createdPost = createPostUseCase.createPost(request.toCommand(effectiveUserId));

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(PostResponse.from(createdPost, null)));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a single post by ID")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Post found"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Post not found")
	})
	public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {

		UUID currentUserId = userDetails != null ? UUID.fromString(userDetails.getUsername()) : null;
		Post post = getPostUseCase.getPost(new GetPostUseCase.Query(id, currentUserId));
		List<PostMedia> postMedias = getPostUseCase.getPostMedia(id);

		return ResponseEntity.status(HttpStatus.OK)
				.body(ApiResponse.ok(PostResponse.from(post, postMedias)));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update caption and/or location of an existing post")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Post updated"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the post owner"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Post not found")
	})
	public ResponseEntity<ApiResponse<PostResponse>> updatePost(
			@PathVariable UUID id,
			@RequestBody @Valid UpdatePostRequest req,
			@AuthenticationPrincipal UserDetails userDetails) {

		UUID userId = UUID.fromString(userDetails.getUsername());
		Post post = updatePostUseCase.updatePost(
				new UpdatePostUseCase.Command(id, userId, req.caption(), req.location()));
		return ResponseEntity.ok(ApiResponse.ok(PostResponse.from(post, null)));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Soft-delete a post (sets status = DELETED)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Post deleted"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the post owner"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Post not found")
	})
	public ResponseEntity<Void> deletePost(
			@PathVariable UUID id,
			@AuthenticationPrincipal UserDetails userDetails) {

		UUID userId = UUID.fromString(userDetails.getUsername());
		deletePostUseCase.deletePost(new DeletePostUseCase.Command(id, userId));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/users/{userId}/posts")
	@Operation(summary = "List all published posts for a given user (paginated)")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of posts")
	public ResponseEntity<ApiResponse<Page<PostResponse>>> getUserPosts(
			@PathVariable UUID userId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "12") int size,
			@AuthenticationPrincipal UserDetails userDetails) {

		UUID currentUserId = userDetails != null ? UUID.fromString(userDetails.getUsername()) : null;
		Page<Post> posts = getUserPostsUseCase.getUserPosts(
				new GetUserPostsUseCase.Query(userId, currentUserId, page, size));
		return ResponseEntity.ok(ApiResponse.ok(posts.map(post -> PostResponse.from(post, null))));
	}
}
