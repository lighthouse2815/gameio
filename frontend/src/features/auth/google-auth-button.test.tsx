import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GoogleAuthButton } from "@/features/auth/google-auth-button";
import {
  resetGoogleIdentityForTests,
  type GoogleCredentialResponse,
  type GoogleIdentityApi,
} from "@/features/auth/google-identity";

function installGoogleApi(api: GoogleIdentityApi) {
  Object.defineProperty(window, "google", {
    configurable: true,
    value: { accounts: { id: api } },
  });
}

describe("GoogleAuthButton", () => {
  beforeEach(() => {
    resetGoogleIdentityForTests();
    document.head.replaceChildren();
    Object.defineProperty(window, "google", {
      configurable: true,
      value: undefined,
    });
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("shows an accessible disabled state when Google is unconfigured", () => {
    render(
      <GoogleAuthButton
        mode="login"
        clientId=""
        onCredential={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("button", { name: /google access unavailable/i }),
    ).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Google access is not configured for this deployment.",
    );
  });

  it("renders sign-up copy and forwards the returned ID token", async () => {
    let callback: ((response: GoogleCredentialResponse) => void) | undefined;
    const onCredential = vi.fn();
    const api: GoogleIdentityApi = {
      initialize: vi.fn((configuration) => {
        callback = configuration.callback;
      }),
      renderButton: vi.fn((parent) => {
        const button = document.createElement("button");
        button.textContent = "Sign up with Google";
        parent.append(button);
      }),
    };
    installGoogleApi(api);

    render(
      <GoogleAuthButton
        mode="register"
        clientId="client-id"
        onCredential={onCredential}
      />,
    );

    expect(
      await screen.findByRole("group", { name: "Sign up with Google" }),
    ).toBeInTheDocument();
    expect(api.renderButton).toHaveBeenCalledWith(
      expect.any(HTMLElement),
      expect.objectContaining({ text: "signup_with" }),
    );

    act(() => callback?.({ credential: "  google-id-token  " }));
    expect(onCredential).toHaveBeenCalledWith("google-id-token");
  });

  it("announces loading failures and exposes a retry action", async () => {
    render(
      <GoogleAuthButton
        mode="login"
        clientId="client-id"
        onCredential={vi.fn()}
      />,
    );
    const script = document.querySelector<HTMLScriptElement>(
      "#google-identity-services",
    );
    act(() => script?.dispatchEvent(new Event("error")));

    const retry = await screen.findByRole("button", {
      name: /retry google access/i,
    });
    expect(retry).toBeEnabled();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Google identity could not load.",
    );
  });

  it("replaces the GIS control with a disabled busy state", async () => {
    const api: GoogleIdentityApi = {
      initialize: vi.fn(),
      renderButton: vi.fn((parent) => {
        parent.append(document.createElement("button"));
      }),
    };
    installGoogleApi(api);
    const { rerender } = render(
      <GoogleAuthButton
        mode="login"
        clientId="client-id"
        onCredential={vi.fn()}
      />,
    );
    await screen.findByRole("group", { name: "Sign in with Google" });

    rerender(
      <GoogleAuthButton
        mode="login"
        clientId="client-id"
        onCredential={vi.fn()}
        busy
      />,
    );

    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: /connecting google identity/i }),
      ).toBeDisabled(),
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      "Google identity is being verified.",
    );
  });
});
