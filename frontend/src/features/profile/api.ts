import type { PlayerProfile } from "@/features/profile/types";
import type { SessionUser } from "@/features/auth/types";
import { apiClient } from "@/lib/api/client";

export const profileApi = {
  byUsername: (username: string) =>
    apiClient.get<PlayerProfile>(
      "/users/" + encodeURIComponent(username),
      { auth: false },
    ),
  updateMe: (input: { avatarUrl: string | null }) =>
    apiClient.patch<SessionUser>("/users/me", input),
};
