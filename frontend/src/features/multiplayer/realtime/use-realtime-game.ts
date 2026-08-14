"use client";

import { useCallback, useEffect, useReducer, useRef } from "react";
import { useSession } from "@/features/auth/hooks";
import {
  initialRealtimeState,
  realtimeGameReducer,
} from "@/features/multiplayer/realtime/reducer";
import {
  gameSocketClient,
  shouldTrackPendingRequest,
  type ClientEventType,
  type GameInputPayload,
  type ServerEnvelope,
} from "@/lib/socket/game-socket-client";
import {
  gameOverPayloadMatches,
  gameStartPayloadMatches,
  isSnapshotForGame,
  roomPayloadMatches,
} from "@/features/multiplayer/realtime/validation";

export function useRealtimeGame(
  roomId: string,
  expectedGameSlug: string,
  mode: "player" | "spectator" = "player",
) {
  const session = useSession();
  const roomValidated = useRef(false);
  const [state, dispatch] = useReducer(
    realtimeGameReducer,
    initialRealtimeState,
  );

  useEffect(() => {
    const unsubscribeEvents = gameSocketClient.subscribeGameState(
      (event: ServerEnvelope) => {
        if (
          event.roomId &&
          event.roomId !== roomId &&
          event.type !== "MATCH_FOUND"
        ) {
          return;
        }
        const rejectProtocol = (code: string, message: string) => {
          roomValidated.current = false;
          gameSocketClient.clearActiveRoom(roomId);
          dispatch({
            type: "server_event",
            event: { type: "ERROR", roomId, payload: { code, message } },
          });
        };
        if (event.type === "ROOM_STATE" || event.type === "MATCH_FOUND") {
          if (!roomPayloadMatches(event.payload, roomId, expectedGameSlug)) {
            rejectProtocol(
              "ROOM_GAME_MISMATCH",
              "This room does not belong to the game opened by this page.",
            );
            return;
          }
          roomValidated.current = true;
        }
        if (event.type === "GAME_START") {
          if (!gameStartPayloadMatches(event.payload, expectedGameSlug)) {
            rejectProtocol(
              "INVALID_GAME_STATE",
              "The server start payload does not match this game engine.",
            );
            return;
          }
          roomValidated.current = true;
        }
        if (event.type === "GAME_STATE") {
          if (
            !roomValidated.current ||
            !isSnapshotForGame(expectedGameSlug, event.payload)
          ) {
            rejectProtocol(
              "INVALID_GAME_STATE",
              "The server snapshot failed this game engine's validation.",
            );
            return;
          }
        }
        if (
          event.type === "GAME_OVER" &&
          !gameOverPayloadMatches(event.payload, expectedGameSlug)
        ) {
          rejectProtocol(
            "INVALID_GAME_STATE",
            "The terminal server snapshot failed validation.",
          );
          return;
        }
        if (event.type === "ROOM_LEFT") {
          roomValidated.current = false;
          gameSocketClient.clearActiveRoom(roomId);
        }
        if (
          event.type === "ERROR" &&
          typeof event.payload === "object" &&
          event.payload !== null &&
          "code" in event.payload &&
          (event.payload.code === "ROOM_EXPIRED" ||
            event.payload.code === "ROOM_FINISHED")
        ) {
          roomValidated.current = false;
          gameSocketClient.clearActiveRoom(roomId);
        }
        dispatch({ type: "server_event", event });
      },
    );
    const unsubscribeStatus = gameSocketClient.subscribeStatus((status) => {
      dispatch({ type: "socket_status", status });
    });
    return () => {
      unsubscribeEvents();
      unsubscribeStatus();
    };
  }, [expectedGameSlug, roomId]);

  useEffect(() => {
    if (!session.data || !roomId) return;
    try {
      if (mode === "spectator") gameSocketClient.spectateRoom(roomId);
      else gameSocketClient.joinRoom(roomId);
    } catch {
      gameSocketClient.connect();
    }
  }, [mode, roomId, session.data]);

  const runCommand = useCallback((
    eventType: ClientEventType,
    command: () => string | null,
  ) => {
    try {
      const requestId = command();
      if (requestId && shouldTrackPendingRequest(eventType)) {
        dispatch({ type: "request_sent", requestId });
      }
      return requestId;
    } catch (error) {
      dispatch({
        type: "server_event",
        event: {
          type: "ERROR",
          payload: {
            code: "CLIENT_NOT_CONNECTED",
            message:
              error instanceof Error
                ? error.message
                : "Realtime command could not be sent.",
          },
        },
      });
      return null;
    }
  }, []);

  const sendInput = useCallback(
    (payload: GameInputPayload) => mode === "spectator" ? null :
      runCommand(
        "GAME_INPUT",
        () => gameSocketClient.sendGameInput(roomId, payload),
      ),
    [mode, roomId, runCommand],
  );

  const ready = useCallback(
    () => runCommand("ROOM_READY", () => gameSocketClient.readyRoom(roomId)),
    [roomId, runCommand],
  );
  const start = useCallback(
    () => runCommand("ROOM_START", () => gameSocketClient.startRoom(roomId)),
    [roomId, runCommand],
  );
  const rematch = useCallback(
    () => runCommand("ROOM_REMATCH", () => gameSocketClient.rematchRoom(roomId)),
    [roomId, runCommand],
  );
  const reconnect = useCallback(() => {
    if (mode === "spectator") gameSocketClient.spectateRoom(roomId);
    else gameSocketClient.joinRoom(roomId);
    gameSocketClient.reconnectNow();
  }, [mode, roomId]);

  const react = useCallback(
    (reaction: "GG" | "NICE" | "WOW" | "REMATCH") =>
      runCommand("ROOM_REACTION", () => gameSocketClient.sendReaction(roomId, reaction)),
    [roomId, runCommand],
  );

  return {
    roomId,
    expectedGameSlug,
    mode,
    session,
    state,
    sendInput,
    ready,
    start,
    rematch,
    reconnect,
    react,
    clearError: () => dispatch({ type: "clear_error" }),
  };
}

export type RealtimeGameController = ReturnType<typeof useRealtimeGame>;
