import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import { PostGrid } from "../../components/posts/PostGrid";

vi.mock("../../components/posts/PostDetailModal", () => ({
  PostDetailModal: () => <div>PostDetailModal</div>,
}));

describe("PostGrid", () => {
  it("should render without errors", async () => {
    render(
      <PostGrid
        posts={[
          {
            id: "1",
            mediaItems: [],
            userId: "1",
            caption: "Test Post",
            location: "VietNam",
            commentCount: 0,
            likeCount: 0,
            likedByCurrentUser: false,
            savedByCurrentUser: false,
            createdAt: "2026-08-01T08:00:00.000Z",
          },
        ]}
      />,
    );
    const numOfLikeCountAndCommentCount = await screen.findAllByText("0");
    expect(numOfLikeCountAndCommentCount).toHaveLength(2);
  });
});
