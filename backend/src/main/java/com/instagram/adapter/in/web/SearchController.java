package com.instagram.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.HashtagSearchResponse;
import com.instagram.adapter.in.web.dto.response.PostSearchResponse;
import com.instagram.adapter.in.web.dto.response.SearchHistoryResponse;
import com.instagram.adapter.in.web.dto.response.UserSearchResponse;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostMedia;
import com.instagram.domain.model.SearchHistory;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStats;
import com.instagram.domain.port.in.post.FindAllPostMediaUseCase;
import com.instagram.domain.port.in.search.ClearSearchHistoryUseCase;
import com.instagram.domain.port.in.search.GetPostsByHashtagUseCase;
import com.instagram.domain.port.in.search.GetSearchHistoryUseCase;
import com.instagram.domain.port.in.search.SearchHashtagsUseCase;
import com.instagram.domain.port.in.search.SearchPostsUseCase;
import com.instagram.domain.port.in.search.SearchUsersUseCase;
import com.instagram.domain.port.in.user.FindAllUserUseCase;
import com.instagram.domain.port.in.user.GetUserStatsUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Search", description = "Search operations")
public class SearchController {

    private final SearchUsersUseCase searchUsersUseCase;
    private final SearchHashtagsUseCase searchHashtagsUseCase;
    private final SearchPostsUseCase searchPostsUseCase;
    private final GetPostsByHashtagUseCase getPostsByHashtagUseCase;
    private final GetSearchHistoryUseCase getSearchHistoryUseCase;
    private final ClearSearchHistoryUseCase clearSearchHistoryUseCase;
    private final FindAllUserUseCase findAllUserUseCase;
    private final FindAllPostMediaUseCase findAllPostMediaUseCase;
    private final GetUserStatsUseCase getUserStatsUseCase;

    public SearchController(SearchUsersUseCase searchUsersUseCase, SearchHashtagsUseCase searchHashtagsUseCase,
            SearchPostsUseCase searchPostsUseCase, GetPostsByHashtagUseCase getPostsByHashtagUseCase,
            GetSearchHistoryUseCase getSearchHistoryUseCase, ClearSearchHistoryUseCase clearSearchHistoryUseCase,
            FindAllUserUseCase findAllUserUseCase, FindAllPostMediaUseCase findAllPostMediaUseCase,
            GetUserStatsUseCase getUserStatsUseCase) {
        this.searchUsersUseCase = searchUsersUseCase;
        this.searchHashtagsUseCase = searchHashtagsUseCase;
        this.searchPostsUseCase = searchPostsUseCase;
        this.getPostsByHashtagUseCase = getPostsByHashtagUseCase;
        this.getSearchHistoryUseCase = getSearchHistoryUseCase;
        this.clearSearchHistoryUseCase = clearSearchHistoryUseCase;
        this.findAllUserUseCase = findAllUserUseCase;
        this.findAllPostMediaUseCase = findAllPostMediaUseCase;
        this.getUserStatsUseCase = getUserStatsUseCase;
    }

    @Nullable
    private UUID currentUserIdOrNull() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return UUID.fromString(userDetails.getUsername());
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }

    private UUID currentUserId() {
        UUID userId = currentUserIdOrNull();
        if (userId == null) {
            throw new IllegalStateException("User is not authenticated");
        }
        return userId;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<?>> search(
            @RequestParam String q,
            @RequestParam String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = currentUserId();
        return switch (type) {
            case "users" -> {
                List<User> users = searchUsersUseCase
                        .searchUsers(new SearchUsersUseCase.Query(q, currentUserId, page, size));
                List<UserStats> userStats = getUserStatsUseCase
                        .findAllUserStatByIds(users.stream().map(User::getId).toList());
                Map<UUID, UserStats> userStatsMap = userStats.stream()
                        .collect(Collectors.toMap(UserStats::userId, Function.identity()));
                yield ResponseEntity.ok(
                        ApiResponse.ok(
                                users.stream()
                                        .map(user -> UserSearchResponse.from(user, userStatsMap.get(user.getId())))
                                        .toList()));
            }
            case "hashtags" -> ResponseEntity.ok(
                    ApiResponse.ok(
                            searchHashtagsUseCase.searchHashtags(new SearchHashtagsUseCase.Query(q, page, size))
                                    .stream()
                                    .map(HashtagSearchResponse::from)
                                    .toList()));
            case "posts" -> {
                List<Post> posts = searchPostsUseCase
                        .searchPosts(new SearchPostsUseCase.Query(q, currentUserId, page, size));
                Map<UUID, User> users = findAllUserUseCase.findAllByIds(posts.stream().map(Post::getUserId).toList())
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
                Map<UUID, List<PostMedia>> postMedias = findAllPostMediaUseCase
                        .findAllByPostIds(posts.stream().map(Post::getId).toList()).stream()
                        .collect(Collectors.groupingBy(PostMedia::getPostId));

                yield ResponseEntity.ok(
                        ApiResponse.ok(
                                posts.stream()
                                        .map(post -> PostSearchResponse.from(post, users.get(post.getUserId()),
                                                postMedias.getOrDefault(post.getId(), List.of())))
                                        .toList()));
            }
            default -> ResponseEntity.badRequest().body(
                    ApiResponse.error("Invalid search type: " + type + ". Use 'users', 'hashtags', or 'posts'."));
        };
    }

    @GetMapping("/search/history")
    public ResponseEntity<ApiResponse<List<SearchHistoryResponse>>> getSearchHistory() {
        UUID currentUserId = currentUserId();
        List<SearchHistory> searchHistories = getSearchHistoryUseCase.getSearchHistory(
                new GetSearchHistoryUseCase.Query(currentUserId, 10));
        List<SearchHistoryResponse> searchHistoryResponses = searchHistories.stream()
                .map(SearchHistoryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(searchHistoryResponses));
    }

    @DeleteMapping("/search/history")
    public ResponseEntity<ApiResponse<Void>> clearSearchHistory() {
        UUID currentUserId = currentUserId();
        clearSearchHistoryUseCase.clearSearchHistory(new ClearSearchHistoryUseCase.Command(currentUserId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/hashtags/{name}/posts")
    public ResponseEntity<ApiResponse<List<PostSearchResponse>>> getPostsByHashtag(
            @PathVariable String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = currentUserId();
        List<Post> posts = getPostsByHashtagUseCase.getPostsByHashtag(
                new GetPostsByHashtagUseCase.Query(name, currentUserId, page, size));
        Map<UUID, User> users = findAllUserUseCase.findAllByIds(posts.stream().map(Post::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, List<PostMedia>> postMedias = findAllPostMediaUseCase
                .findAllByPostIds(posts.stream().map(Post::getId).toList()).stream()
                .collect(Collectors.groupingBy(PostMedia::getPostId));

        List<PostSearchResponse> postSearchResponses = posts.stream()
                .map(post -> PostSearchResponse.from(post, users.get(post.getUserId()),
                        postMedias.getOrDefault(post.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(postSearchResponses));
    }
}
