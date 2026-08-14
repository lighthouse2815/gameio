import type {
  GameRoom,
  MatchmakingTicket,
  RoomListResponse,
} from "@/features/multiplayer/types";
import { apiClient } from "@/lib/api/client";
import { isApiError } from "@/lib/api/api-error";

export const multiplayerApi = {
  rooms: (gameId?: string, status: "WAITING" | "PLAYING" = "WAITING") =>
    apiClient.get<RoomListResponse>("/rooms", {
      query: { gameId, status, page: 0, size: 30 },
    }),
  createRoom: (input: {
    gameId: string;
    maxPlayers: number;
    privateRoom: boolean;
  }) => apiClient.post<GameRoom>("/rooms", input),
  joinRoom: (roomCode: string) =>
    apiClient.post<GameRoom>("/rooms/join", { roomCode }),
  room: (roomId: string) =>
    apiClient.get<GameRoom>("/rooms/" + encodeURIComponent(roomId)),
  leaveRoom: (roomId: string) =>
    apiClient.post<void>("/rooms/" + encodeURIComponent(roomId) + "/leave"),
  ready: (roomId: string) =>
    apiClient.post<GameRoom>("/rooms/" + encodeURIComponent(roomId) + "/ready"),
  start: (roomId: string) =>
    apiClient.post<GameRoom>("/rooms/" + encodeURIComponent(roomId) + "/start"),
  quickMatch: (gameId: string) =>
    apiClient.post<MatchmakingTicket>("/matchmaking", { gameId }),
  currentMatch: async () => {
    try {
      return await apiClient.get<MatchmakingTicket>("/matchmaking");
    } catch (error) {
      if (
        isApiError(error) &&
        (error.status === 404 ||
          error.code === "MATCHMAKING_TICKET_NOT_FOUND")
      ) {
        return null;
      }
      throw error;
    }
  },
  leaveQueue: () => apiClient.delete<void>("/matchmaking"),
};
