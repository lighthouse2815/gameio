import type {
  GameOverPayload,
  GameSnapshot,
  GameStartPayload,
  OpponentDisconnected,
  RealtimeAction,
  RealtimeError,
  RealtimeGameState,
} from "@/features/multiplayer/realtime/types";
import type { GameRoom } from "@/features/multiplayer/types";

export const initialRealtimeState: RealtimeGameState = {
  connection: "idle",
  room: null,
  gameSlug: null,
  matchId: null,
  snapshot: null,
  gameOver: null,
  error: null,
  opponentDisconnected: null,
  pendingRequestIds: [],
  lastRequestId: null,
};

export function snapshotSequence(snapshot: unknown) {
  if (
    typeof snapshot === "object" &&
    snapshot !== null &&
    "sequence" in snapshot &&
    typeof snapshot.sequence === "number"
  ) {
    return snapshot.sequence;
  }
  return -1;
}

function acknowledge(
  state: RealtimeGameState,
  requestId?: string | null,
): Pick<RealtimeGameState, "pendingRequestIds" | "lastRequestId"> {
  if (!requestId) {
    return {
      pendingRequestIds: state.pendingRequestIds,
      lastRequestId: state.lastRequestId,
    };
  }
  return {
    pendingRequestIds: state.pendingRequestIds.filter(
      (candidate) => candidate !== requestId,
    ),
    lastRequestId: requestId,
  };
}

export function realtimeGameReducer(
  state: RealtimeGameState,
  action: RealtimeAction,
): RealtimeGameState {
  if (action.type === "socket_status") {
    return { ...state, connection: action.status };
  }
  if (action.type === "request_sent") {
    return {
      ...state,
      pendingRequestIds: [
        ...state.pendingRequestIds.slice(-63),
        action.requestId,
      ],
    };
  }
  if (action.type === "clear_error") {
    return { ...state, error: null };
  }

  const { event } = action;
  const acknowledged = acknowledge(state, event.requestId);
  if (event.type === "ROOM_STATE" || event.type === "MATCH_FOUND") {
    const room = event.payload as GameRoom;
    const returnedToWaitingRoom =
      room.status === "WAITING" && state.gameOver !== null;
    const disconnectedPlayer = state.opponentDisconnected
      ? room.players.find(
          (player) => player.id === state.opponentDisconnected?.userId,
        )
      : null;
    return {
      ...state,
      ...acknowledged,
      room,
      gameSlug: room.gameSlug,
      matchId: returnedToWaitingRoom ? null : state.matchId,
      snapshot: returnedToWaitingRoom ? null : state.snapshot,
      gameOver: returnedToWaitingRoom ? null : state.gameOver,
      error: null,
      opponentDisconnected:
        disconnectedPlayer && !disconnectedPlayer.connected
          ? state.opponentDisconnected
          : null,
    };
  }
  if (event.type === "ROOM_LEFT") {
    return {
      ...state,
      ...acknowledged,
      room: null,
      gameSlug: null,
      matchId: null,
      snapshot: null,
      gameOver: null,
      error: null,
      opponentDisconnected: null,
      pendingRequestIds: [],
    };
  }
  if (event.type === "GAME_START") {
    const payload = event.payload as GameStartPayload;
    return {
      ...state,
      ...acknowledged,
      gameSlug: payload.gameSlug,
      matchId: payload.matchId,
      snapshot: payload.state,
      gameOver: null,
      error: null,
      opponentDisconnected: null,
    };
  }
  if (event.type === "GAME_STATE") {
    const snapshot = event.payload as GameSnapshot;
    if (
      state.snapshot &&
      snapshotSequence(snapshot) < snapshotSequence(state.snapshot)
    ) {
      return { ...state, ...acknowledged };
    }
    return {
      ...state,
      ...acknowledged,
      snapshot,
      error: null,
    };
  }
  if (event.type === "GAME_OVER") {
    const gameOver = event.payload as GameOverPayload;
    return {
      ...state,
      ...acknowledged,
      matchId: gameOver.matchId,
      snapshot: gameOver.finalState,
      gameOver,
      error: null,
      opponentDisconnected: null,
    };
  }
  if (event.type === "OPPONENT_DISCONNECTED") {
    return {
      ...state,
      ...acknowledged,
      opponentDisconnected: event.payload as OpponentDisconnected,
    };
  }
  if (event.type === "ERROR") {
    const payload = event.payload as Omit<RealtimeError, "requestId">;
    const error = {
      code: payload.code ?? "REALTIME_ERROR",
      message: payload.message ?? "Realtime command failed.",
      requestId: event.requestId,
    };
    if (
      error.code === "ROOM_EXPIRED" ||
      error.code === "ROOM_FINISHED" ||
      error.code === "ROOM_GAME_MISMATCH" ||
      error.code === "INVALID_GAME_STATE"
    ) {
      return {
        ...state,
        ...acknowledged,
        room: null,
        gameSlug: null,
        matchId: null,
        snapshot: null,
        gameOver: null,
        opponentDisconnected: null,
        pendingRequestIds: [],
        error,
      };
    }
    return {
      ...state,
      ...acknowledged,
      error,
    };
  }
  return { ...state, ...acknowledged };
}
