import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/toast";
import FlappyBirdGame from "@/games/flappy-bird/flappy-bird-game";
import { FlappyEngine } from "@/games/flappy-bird/engine";
import { I18nProvider } from "@/lib/i18n/i18n-provider";

const authHarness = vi.hoisted(() => ({
  user: null as null | {
    id: string;
    username: string;
    email: string;
    level: number;
    exp: number;
  },
  loading: false,
}));

const apiHarness = vi.hoisted(() => ({
  createSession: vi.fn(),
  complete: vi.fn(),
}));

vi.mock("@/features/auth/hooks", () => ({
  useSession: () => ({
    data: authHarness.user,
    isLoading: authHarness.loading,
  }),
}));

vi.mock("@/features/games/game-results-api", () => ({
  gameResultsApi: {
    createSession: apiHarness.createSession,
    complete: apiHarness.complete,
  },
}));

function renderGame() {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  });
  return render(
    <I18nProvider initialLocale="en">
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <FlappyBirdGame />
        </ToastProvider>
      </QueryClientProvider>
    </I18nProvider>,
  );
}

describe("Flappy Bird game", () => {
  beforeEach(() => {
    authHarness.user = null;
    authHarness.loading = false;
    apiHarness.createSession.mockReset();
    apiHarness.complete.mockReset();
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockImplementation(
      () => null,
    );
    vi.stubGlobal("requestAnimationFrame", vi.fn(() => 1));
    vi.stubGlobal("cancelAnimationFrame", vi.fn());
  });

  afterEach(() => {
    cleanup();
    localStorage.clear();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("starts offline without an account and captures only active game keys", () => {
    renderGame();

    expect(screen.getByRole("button", { name: "Play offline" })).toBeEnabled();
    expect(screen.getByRole("link", { name: "Sign in for online rank" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Play offline" }));

    expect(screen.getByRole("button", { name: "Flap" })).toBeEnabled();
    const flightKey = new KeyboardEvent("keydown", {
      key: "ArrowUp",
      code: "ArrowUp",
      cancelable: true,
    });
    window.dispatchEvent(flightKey);
    expect(flightKey.defaultPrevented).toBe(true);

    const input = document.createElement("input");
    document.body.appendChild(input);
    const typingKey = new KeyboardEvent("keydown", {
      key: "w",
      code: "KeyW",
      bubbles: true,
      cancelable: true,
    });
    input.dispatchEvent(typingKey);
    expect(typingKey.defaultPrevented).toBe(false);
    input.remove();
  });

  it("starts a seeded online run while retaining the offline choice", async () => {
    authHarness.user = {
      id: "player-1",
      username: "Pilot",
      email: "pilot@example.com",
      level: 1,
      exp: 0,
    };
    const seed = 7_936;
    apiHarness.createSession.mockResolvedValue({
      sessionId: "session-1",
      gameSlug: "flappy-bird",
      seed,
      initialState: new FlappyEngine(seed).state(),
      expiresAt: "2026-08-14T00:00:00Z",
    });
    renderGame();

    expect(screen.getByRole("button", { name: "Play online" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Play offline" })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: "Play online" }));

    await waitFor(() => {
      expect(apiHarness.createSession).toHaveBeenCalledWith("flappy-bird");
      expect(screen.getByRole("button", { name: "Flap" })).toBeEnabled();
    });
    expect(screen.getAllByText("online").length).toBeGreaterThan(0);
  });
});
