import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/toast";
import { AuthForm } from "@/features/auth/auth-form";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

function renderForm(mode: "login" | "register") {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AuthForm mode={mode} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("authentication provider entry points", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_GOOGLE_CLIENT_ID", "");
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it.each([
    ["login", "Enter Gameio"],
    ["register", "Create player"],
  ] as const)(
    "keeps the Google entry point next to the %s form",
    (mode, passwordAction) => {
      renderForm(mode);

      expect(
        screen.getByRole("button", { name: passwordAction }),
      ).toBeEnabled();
      expect(
        screen.getByRole("button", { name: /google access unavailable/i }),
      ).toBeDisabled();
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Google access is not configured for this deployment.",
      );
    },
  );
});
