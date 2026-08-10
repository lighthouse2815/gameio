import type {
  FriendPresence,
  FriendRequest,
  FriendRequestsResponse,
} from "@/features/friends/types";
import { apiClient } from "@/lib/api/client";

export const friendApi = {
  list: () => apiClient.get<FriendPresence[]>("/friends"),
  requests: () =>
    apiClient.get<FriendRequestsResponse>("/friends/requests"),
  send: (username: string) =>
    apiClient.post<FriendRequest>("/friends/requests", { username }),
  accept: (requestId: string) =>
    apiClient.post<void>(
      "/friends/requests/" + encodeURIComponent(requestId) + "/accept",
    ),
  reject: (requestId: string) =>
    apiClient.post<void>(
      "/friends/requests/" + encodeURIComponent(requestId) + "/reject",
    ),
  remove: (username: string) =>
    apiClient.delete<void>("/friends/" + encodeURIComponent(username)),
};
