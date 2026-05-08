# TASK-5.9 — Unit & Integration Tests: Feed

## Overview

Write tests for the three feed-related classes. Follow the project's convention: JUnit 5 + Mockito for domain service unit tests, `@DataJpaTest` for persistence adapters, and `@WebMvcTest` + MockMvc for controllers.

## Requirements

- Unit test `FeedService` in isolation (mock `FeedRepository`).
- Integration test `FeedJpaQueryAdapter` with a real in-memory data set using `@DataJpaTest`.
- Controller test `FeedController` verifying cursor serialisation and delegation.

## File Locations

```
backend/src/test/java/com/instagram/
├── domain/service/FeedServiceTest.java
├── adapter/out/persistence/FeedJpaQueryAdapterIT.java
└── adapter/in/web/FeedControllerTest.java
```

---

## Checklist

### `FeedServiceTest.java`

- [ ] Set up: mock `FeedRepository` with Mockito (`@ExtendWith(MockitoExtension.class)`)
- [ ] Inject `FeedService` via constructor with the mocked repository

- [ ] Test: `getHomeFeed_returnsPostsFromRepository`:
  ```java
  // Arrange
  UUID userId = UUID.randomUUID();
  Post post1 = buildPost(UUID.randomUUID());
  Post post2 = buildPost(UUID.randomUUID());
  when(feedRepository.getHomeFeed(userId, null, 20))
      .thenReturn(List.of(post1, post2));

  // Act
  var result = feedService.getHomeFeed(new GetHomeFeedUseCase.Query(userId, null, 20));

  // Assert
  assertThat(result.posts()).hasSize(2);
  assertThat(result.nextCursor()).isNull(); // 2 < limit=20, so no next page
  ```

- [ ] Test: `getHomeFeed_setsNextCursor_whenFullPageReturned`:
  ```java
  // Returns exactly `limit` posts → nextCursor = id of last post
  List<Post> posts = generatePosts(20); // helper that creates 20 posts
  when(feedRepository.getHomeFeed(userId, null, 20)).thenReturn(posts);

  var result = feedService.getHomeFeed(new GetHomeFeedUseCase.Query(userId, null, 20));

  assertThat(result.nextCursor()).isEqualTo(posts.get(19).getId());
  ```

- [ ] Test: `getExploreFeed_delegatesToRepository` — same pattern as above

---

### `FeedJpaQueryAdapterIT.java`

- [ ] Annotate with `@DataJpaTest`
- [ ] Use `@Sql` or repository `saveAll` to set up test data: 2 users, 1 follow relationship, 3 posts (2 from followed user, 1 from stranger)

- [ ] Test: `findHomeFeed_returnsOnlyFollowedUsersPosts`:
  ```java
  List<PostJpaEntity> feed = feedJpaRepository.findHomeFeed(followerId, null, 20);

  assertThat(feed).hasSize(2);
  assertThat(feed).allMatch(p -> p.getUserId().equals(followedUserId));
  ```

- [ ] Test: `findExploreFeed_excludesFollowedUsers`:
  ```java
  List<PostJpaEntity> explore = feedJpaRepository.findExploreFeed(followerId, null, 20);

  assertThat(explore).hasSize(1);
  assertThat(explore.get(0).getUserId()).isEqualTo(strangerId);
  ```

- [ ] Test: `findHomeFeed_cursorPagination_returnsPostsBeforeCursor`:
  ```java
  // cursor = id of the first post → should return nothing (no older posts)
  List<PostJpaEntity> page2 = feedJpaRepository
      .findHomeFeed(followerId, posts.get(0).getId(), 20);

  assertThat(page2).isEmpty();
  ```

---

### `FeedControllerTest.java`

- [ ] Annotate with `@WebMvcTest(FeedController.class)`
- [ ] Mock `GetHomeFeedUseCase`, `GetExploreFeedUseCase`, `FeedRepository`

- [ ] Test: `GET /api/v1/feed` returns 200 with `posts` array and `nextCursor`:
  ```java
  when(getHomeFeedUseCase.getHomeFeed(any()))
      .thenReturn(new GetHomeFeedUseCase.FeedPage(List.of(somePost), someUuid));

  mockMvc.perform(get("/api/v1/feed").param("limit", "1")
          .with(jwtToken()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.posts").isArray())
      .andExpect(jsonPath("$.data.nextCursor").isNotEmpty());
  ```

- [ ] Test: `GET /api/v1/feed?cursor=<uuid>` passes cursor to use case:
  ```java
  UUID cursor = UUID.randomUUID();
  mockMvc.perform(get("/api/v1/feed").param("cursor", cursor.toString())
          .with(jwtToken()))
      .andExpect(status().isOk());

  verify(getHomeFeedUseCase).getHomeFeed(
      argThat(q -> cursor.equals(q.cursor())));
  ```

- [ ] Test: `GET /api/v1/feed?cursor=invalid` returns 400 (bad UUID)

- [ ] Test: `GET /api/v1/explore/hashtags` returns 200 with hashtag list

## Notes

- Use the same JWT mock helper already established in `LikeControllerTest` or `PostControllerTest`.
- `@DataJpaTest` spins up an H2 in-memory database — the native SQL uses PostgreSQL-specific syntax (`ON CONFLICT`, `is_approved`). Use `@AutoConfigureTestDatabase(replace = NONE)` with a TestContainers PostgreSQL instance if the native queries fail on H2. Check how existing integration tests handle this.
