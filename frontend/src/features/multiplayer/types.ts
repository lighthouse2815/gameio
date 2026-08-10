import type { PageResponse } from "@/types/page";

export type RoomPlayer = {
  id: string;
  username: string;
  ready: boolean;
  owner: boolean;
  connected: boolean;
};

export type GameRoom = {
  roomId: string;
  roomCode: string;
  gameId: string;
  gameSlug: string;
  gameName: string;
  ownerId: string;
  maxPlayers: number;
  privateRoom: boolean;
  status: "WAITING" | "PLAYING" | "FINISHED";
  players: RoomPlayer[];
  createdAt: string;
};

export type RoomListResponse = PageResponse<GameRoom> | GameRoom[];

export type MatchmakingTicket = {
  ticketId: string;
  gameId: string;
  status: "QUEUED" | "MATCH_FOUND";
  roomId?: string;
  joinedAt: string;
};
