import { QueryClient } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { authApi } from "@/features/auth/api";
import {
  applyAuthSession,
  sessionQueryKey,
} from "@/features/auth/hooks";
import type { AuthResponse } from "@/features/auth/types";
import { apiClient } from "@/lib/api/client";
import { tokenVault } from "@/lib/api/token-vault";

const RESPONSE: AuthResponse = {
  tokenType: "Bearer",
  accessToken: "gameio-access-token",
  accessExpiresAt: "2026-08-13T12:00:00Z",
  user: {
    id: "user-id",
    username: "google_player",
    email: "player@example.com",
    level: 1,
    exp: 0,
  },
};

describe("Google authentication session path", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("posts the Google ID token to the auth endpoint without bearer auth", async () => {
    const post = vi.spyOn(apiClient, "post").mockResolvedValue(RESPONSE);

    await expect(authApi.google({ idToken: "google-id-token" })).resolves.toBe(
      RESPONSE,
    );
    expect(post).toHaveBeenCalledWith(
      "/auth/google",
      { idToken: "google-id-token" },
      { auth: false },
    );
  });

  it("applies Google responses through the same token and session cache path", () => {
    const queryClient = new QueryClient();
    const setAccessToken = vi
      .spyOn(tokenVault, "setAccessToken")
      .mockImplementation(() => undefined);

    applyAuthSession(queryClient, RESPONSE);

    expect(setAccessToken).toHaveBeenCalledWith("gameio-access-token");
    expect(queryClient.getQueryData(sessionQueryKey)).toEqual(RESPONSE.user);
  });
});
