import { describe, expect, it } from "vitest";
import {
  initialRealtimeState,
  realtimeGameReducer,
} from "@/features/multiplayer/realtime/reducer";
import type { TicTacToeSnapshot } from "@/features/multiplayer/realtime/types";
import type { GameRoom } from "@/features/multiplayer/types";

const room: GameRoom = {
  roomId: "room-1",
  roomCode: "ABC123",
  gameId: "game-1",
  gameSlug: "tic-tac-toe",
  gameName: "Tic Tac Toe",
  ownerId: "user-1",
  maxPlayers: 2,
  privateRoom: false,
  status: "PLAYING",
  players: [
    {
      id: "user-1",
      username: "alpha",
      ready: true,
      owner: true,
      connected: true,
    },
    {
      id: "user-2",
      username: "beta",
      ready: true,
      owner: false,
      connected: true,
    },
  ],
  createdAt: "2026-08-10T00:00:00Z",
};

const snapshot = (sequence: number): TicTacToeSnapshot => ({
  sequence,
  board: [
    ["", "", ""],
    ["", "X", ""],
    ["", "", ""],
  ],
  currentTurnPlayerId: "user-2",
  winnerId: null,
  draw: false,
});

describe("realtime game reducer", () => {
  it("applies room and game-start server state", () => {
    const withRoom = realtimeGameReducer(initialRealtimeState, {
      type: "server_event",
      event: { type: "ROOM_STATE", roomId: room.roomId, payload: room },
    });
    const started = realtimeGameReducer(withRoom, {
      type: "server_event",
      event: {
        type: "GAME_START",
        roomId: room.roomId,
        payload: {
          matchId: "match-1",
          gameId: room.gameId,
          gameSlug: room.gameSlug,
          players: room.players,
          startedAt: "2026-08-10T00:00:01Z",
          state: snapshot(0),
        },
      },
    });
    expect(started.room).toEqual(room);
    expect(started.matchId).toBe("match-1");
    expect(started.snapshot).toEqual(snapshot(0));
  });

  it("ignores stale snapshots while acknowledging their request", () => {
    const current = {
      ...initialRealtimeState,
      snapshot: snapshot(7),
      pendingRequestIds: ["request-1"],
    };
    const result = realtimeGameReducer(current, {
      type: "server_event",
      event: {
        type: "GAME_STATE",
        requestId: "request-1",
        payload: snapshot(6),
      },
    });
    expect(result.snapshot).toEqual(snapshot(7));
    expect(result.pendingRequestIds).toEqual([]);
    expect(result.lastRequestId).toBe("request-1");
  });

  it("clears a rejected game input and exposes the server error", () => {
    const pending = realtimeGameReducer(initialRealtimeState, {
      type: "request_sent",
      requestId: "move-4",
    });
    const rejected = realtimeGameReducer(pending, {
      type: "server_event",
      event: {
        type: "ERROR",
        requestId: "move-4",
        payload: {
          code: "INVALID_GAME_ACTION",
          message: "It is not this player's turn",
        },
      },
    });
    expect(rejected.pendingRequestIds).toEqual([]);
    expect(rejected.error?.code).toBe("INVALID_GAME_ACTION");
  });

  it("keeps disconnect truth across game snapshots until ROOM_STATE reconnects", () => {
    const disconnected = realtimeGameReducer(
      { ...initialRealtimeState, room, snapshot: snapshot(2) },
      {
        type: "server_event",
        event: {
          type: "OPPONENT_DISCONNECTED",
          payload: { userId: "user-2", reconnectGraceSeconds: 60 },
        },
      },
    );
    const updated = realtimeGameReducer(disconnected, {
      type: "server_event",
      event: { type: "GAME_STATE", payload: snapshot(3) },
    });
    expect(updated.opponentDisconnected?.userId).toBe("user-2");

    const reconnectedRoom: GameRoom = {
      ...room,
      players: room.players.map((player) =>
        player.id === "user-2" ? { ...player, connected: true } : player,
      ),
    };
    const reconnected = realtimeGameReducer(updated, {
      type: "server_event",
      event: { type: "ROOM_STATE", payload: reconnectedRoom },
    });
    expect(reconnected.opponentDisconnected).toBeNull();
  });

  it("retains the banner while ROOM_STATE still marks that player offline", () => {
    const offlineRoom: GameRoom = {
      ...room,
      players: room.players.map((player) =>
        player.id === "user-2" ? { ...player, connected: false } : player,
      ),
    };
    const state = realtimeGameReducer(
      {
        ...initialRealtimeState,
        opponentDisconnected: {
          userId: "user-2",
          reconnectGraceSeconds: 60,
        },
      },
      { type: "server_event", event: { type: "ROOM_STATE", payload: offlineRoom } },
    );
    expect(state.opponentDisconnected?.userId).toBe("user-2");
  });

  it("uses the server final state and progression on game over", () => {
    const finalState = {
      ...snapshot(5),
      winnerId: "user-1",
      currentTurnPlayerId: null,
    };
    const result = realtimeGameReducer(initialRealtimeState, {
      type: "server_event",
      event: {
        type: "GAME_OVER",
        payload: {
          matchId: "match-1",
          finalState,
          progression: [
            {
              userId: "user-1",
              result: "WIN",
              score: 1,
              expAwarded: 20,
              level: 2,
              ratingBefore: 1000,
              ratingAfter: 1016,
              ratingDelta: 16,
              unlockedAchievements: [],
            },
          ],
        },
      },
    });
    expect(result.snapshot).toEqual(finalState);
    expect(result.gameOver?.progression[0].result).toBe("WIN");
  });

  it("clears the completed engine when the room returns for a rematch", () => {
    const completed = {
      ...initialRealtimeState,
      room,
      gameSlug: room.gameSlug,
      matchId: "match-1",
      snapshot: snapshot(5),
      gameOver: {
        matchId: "match-1",
        finalState: snapshot(5),
        progression: [],
      },
    };
    const waitingRoom: GameRoom = {
      ...room,
      status: "WAITING",
      players: room.players.map((player) => ({ ...player, ready: false })),
    };

    const result = realtimeGameReducer(completed, {
      type: "server_event",
      event: { type: "ROOM_STATE", roomId: room.roomId, payload: waitingRoom },
    });

    expect(result.room?.status).toBe("WAITING");
    expect(result.matchId).toBeNull();
    expect(result.snapshot).toBeNull();
    expect(result.gameOver).toBeNull();
  });

  it("clears an unrestorable room and stale snapshot on ROOM_EXPIRED", () => {
    const active = {
      ...initialRealtimeState,
      room,
      gameSlug: room.gameSlug,
      matchId: "match-1",
      snapshot: snapshot(9),
      pendingRequestIds: ["move-9"],
    };
    const result = realtimeGameReducer(active, {
      type: "server_event",
      event: {
        type: "ERROR",
        requestId: "rejoin-1",
        payload: {
          code: "ROOM_EXPIRED",
          message: "Active engine state expired after a server restart",
        },
      },
    });
    expect(result.room).toBeNull();
    expect(result.snapshot).toBeNull();
    expect(result.matchId).toBeNull();
    expect(result.pendingRequestIds).toEqual([]);
    expect(result.error?.code).toBe("ROOM_EXPIRED");
  });

  it("clears every active game field after a confirmed ROOM_LEFT", () => {
    const active = {
      ...initialRealtimeState,
      room,
      gameSlug: room.gameSlug,
      matchId: "match-1",
      snapshot: snapshot(9),
      pendingRequestIds: ["leave-1"],
    };
    const result = realtimeGameReducer(active, {
      type: "server_event",
      event: {
        type: "ROOM_LEFT",
        roomId: room.roomId,
        requestId: "leave-1",
        payload: { userId: room.ownerId },
      },
    });
    expect(result.room).toBeNull();
    expect(result.gameSlug).toBeNull();
    expect(result.matchId).toBeNull();
    expect(result.snapshot).toBeNull();
    expect(result.gameOver).toBeNull();
    expect(result.pendingRequestIds).toEqual([]);
    expect(result.lastRequestId).toBe("leave-1");
  });
});
