import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/toast";
import SnakeGame from "@/games/snake/snake-game";

const phaserHarness = vi.hoisted(() => ({
  gameConstructor: vi.fn(),
  shouldThrow: false,
}));

vi.mock("@/features/auth/hooks", () => ({
  useSession: () => ({ data: null, isLoading: false }),
}));

vi.mock("phaser", () => {
  const graphics = {
    clear: vi.fn(),
    fillRect: vi.fn(),
    fillStyle: vi.fn(),
    lineBetween: vi.fn(),
    lineStyle: vi.fn(),
  };

  class Scene {
    add = { graphics: () => graphics };
    input = { keyboard: { on: vi.fn() } };
  }

  class Game {
    constructor(config: {
      parent: HTMLElement;
      scene: new () => { create(): void };
    }) {
      phaserHarness.gameConstructor(config);
      if (phaserHarness.shouldThrow) {
        throw new Error("Renderer initialization failed");
      }
      config.parent.appendChild(document.createElement("canvas"));
      new config.scene().create();
    }

    destroy() {}
  }

  return {
    AUTO: "AUTO",
    Game,
    Scale: { CENTER_BOTH: "CENTER_BOTH", FIT: "FIT" },
    Scene,
  };
});

function renderSnake() {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <SnakeGame />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("Snake Phaser renderer", () => {
  afterEach(() => {
    cleanup();
    localStorage.clear();
    phaserHarness.gameConstructor.mockClear();
    phaserHarness.shouldThrow = false;
  });

  it("starts with the named exports provided by Phaser 4", async () => {
    const { container } = renderSnake();

    fireEvent.click(screen.getByRole("button", { name: "Start run" }));

    await waitFor(() => {
      expect(phaserHarness.gameConstructor).toHaveBeenCalledOnce();
    });
    expect(container.querySelector(".game-canvas canvas")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Move up" })).toBeEnabled();
  });

  it("returns to ready state when the renderer cannot initialize", async () => {
    phaserHarness.shouldThrow = true;
    renderSnake();

    fireEvent.click(screen.getByRole("button", { name: "Start run" }));

    expect(
      await screen.findByText("Snake engine unavailable"),
    ).toBeInTheDocument();
    expect(screen.getByText("ready")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Move up" })).toBeDisabled();
  });
});
