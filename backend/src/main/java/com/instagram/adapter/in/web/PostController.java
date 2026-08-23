package com.instagram.adapter.in.web;

import com.instagram.adapter.in.web.dto.request.CreatePostRequest;
import com.instagram.adapter.in.web.dto.request.UpdatePostRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.PostPageResponse;
import com.instagram.adapter.in.web.dto.response.PostResponse;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostMedia;
import com.instagram.domain.port.in.*;
import com.instagram.domain.port.in.post.FindAllPostMediaUseCase;
import com.instagram.infrastructure.security.HtmlSanitizer;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Posts", description = "CRUD operations for posts")
public class PostController {

	private final CreatePostUseCase createPostUseCase;
	private final GetPostUseCase getPostUseCase;
	private final UpdatePostUseCase updatePostUseCase;
	private final DeletePostUseCase deletePostUseCase;
	private final GetUserPostsUseCase getUserPostsUseCase;
	private final FindAllPostMediaUseCase findAllPostMediaUseCase;
	private final HtmlSanitizer htmlSanitizer;

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

		List<CreatePostUseCase.MediaItem> items = request.mediaItems().stream()
				.map(m -> new CreatePostUseCase.MediaItem(
						m.mediaKey(), m.mediaType(), m.width(), m.height(),
						m.duration(), m.fileSizeBytes(), m.sortOrder()))
				.toList();

		CreatePostUseCase.Command command = new CreatePostUseCase.Command(
				effectiveUserId,
				htmlSanitizer.sanitize(request.caption()),
				htmlSanitizer.sanitize(request.location()),
				items);

		Post createdPost = createPostUseCase.createPost(command);
		log.info("Post created id={} userId={}", createdPost.getId(), effectiveUserId);

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
		log.debug("getPost id={} requestedBy={}", id, currentUserId);
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
				new UpdatePostUseCase.Command(id, userId,
						htmlSanitizer.sanitize(req.caption()),
						htmlSanitizer.sanitize(req.location())));
		log.info("Post updated id={} userId={}", id, userId);
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
		log.info("Post deleted id={} userId={}", id, userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/users/{userId}/posts")
	@Operation(summary = "List all published posts for a given user (paginated)")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of posts")
	public ResponseEntity<ApiResponse<PostPageResponse>> getUserPosts(
			@PathVariable UUID userId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "12") int size,
			@AuthenticationPrincipal UserDetails userDetails) {

		UUID currentUserId = userDetails != null ? UUID.fromString(userDetails.getUsername()) : null;
		log.debug("getUserPosts targetUserId={} page={} size={} requestedBy={}", userId, page, size, currentUserId);
		List<Post> posts = getUserPostsUseCase.getUserPosts(
				new GetUserPostsUseCase.Query(userId, currentUserId, page, size));

		List<UUID> postIds = posts.stream().map(Post::getId).toList();
		Map<UUID, List<PostMedia>> mediaByPostId = findAllPostMediaUseCase.findAllByPostIds(postIds).stream()
				.collect(Collectors.groupingBy(PostMedia::getPostId));

		List<PostResponse> responses = posts.stream()
				.map(post -> PostResponse.from(post, mediaByPostId.get(post.getId())))
				.toList();
		return ResponseEntity.ok(ApiResponse.ok(PostPageResponse.of(responses, page, size)));
	}
}
