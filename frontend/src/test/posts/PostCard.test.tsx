import { render, screen } from "@testing-library/react";
import { PostCard } from "../../components/posts/PostCard";
import { describe, it, expect, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { FollowStatus } from "../../types/follow";

vi.mock("../../hooks/useAuth", () => ({
  useAuth: () => ({
    profile: {
      user: {
        id: "1",
        username: "user1",
      },
      followStatus: FollowStatus.ACCEPTED,
    },
  }),
}));

vi.mock("../../components/posts/LikeButton", () => ({
  LikeButton: () => <div>LikeButton</div>,
}));
vi.mock("../../components/posts/SaveButton", () => ({
  SaveButton: () => <div>SaveButton</div>,
}));
vi.mock("../../components/posts/ShareMenu", () => ({
  ShareMenu: () => <div>ShareMenu</div>,
}));
vi.mock("../../components/posts/LikersTooltip", () => ({
  LikersTooltip: () => <div>LikersTooltip</div>,
}));
vi.mock("../../components/posts/PostDetailModal", () => ({
  PostDetailModal: () => <div>PostDetailModal</div>,
}));
vi.mock("../../components/moderation/ReportDialog", () => ({
  ReportDialog: () => <div>ReportDialog</div>,
}));

vi.mock("../../api/usersApi", () => ({
  usersApi: {
    getUserById: vi.fn().mockResolvedValue({ id: "1", username: "user1" }),
  },
}));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
});

describe("PostCard", () => {
  it("should render without errors", async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <PostCard
          post={{
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
          }}
        />
      </QueryClientProvider>,
    );

    expect(screen.getByText("Test Post")).toBeInTheDocument();
    // user data arrives asynchronously via the useMutation call in useEffect
    const userElements = await screen.findAllByText("user1");
    expect(userElements.length).toBeGreaterThan(0);
  });
});
